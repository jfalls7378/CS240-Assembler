import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A small file-based MIPS assembler for the supplied FizzBuzz program. */
public class Assembler {
    static final int TEXT_BASE = 0x00400000, DATA_BASE = 0x10010000;
    static final Map<String,Integer> REG = new HashMap<>();
    static {
        String[] names = {"zero","at","v0","v1","a0","a1","a2","a3","t0","t1","t2","t3","t4","t5","t6","t7","s0","s1","s2","s3","s4","s5","s6","s7","t8","t9","k0","k1","gp","sp","fp","ra"};
        for (int i=0;i<names.length;i++) { REG.put(names[i],i); REG.put("$"+names[i],i); REG.put(Integer.toString(i),i); }
    }
    static class Item { int address, line; String text; Item(int a,int l,String t){address=a;line=l;text=t;} }
    static class Result { List<Integer> words; byte[] data; Result(List<Integer>w,byte[]d){words=w;data=d;} }

    static void error(int line,String msg) { throw new IllegalArgumentException("line "+line+": "+msg); }
    static String noComment(String s) { int p=s.indexOf('#'); return (p<0?s:s.substring(0,p)).trim(); }
    static String[] args(String s) { if(s.trim().isEmpty()) return new String[0]; return s.split("\\s*,\\s*"); }
    static int reg(String s,int line) { Integer n=REG.get(s.trim()); if(n==null) error(line,"unknown register "+s); return n; }
    static int constant(String s,Map<String,Integer> labels,int line) {
        s=s.trim(); if(labels.containsKey(s)) return labels.get(s);
        if(s.startsWith("'") && s.endsWith("'") && s.length()>=3) {
            String q=s.substring(1,s.length()-1); if(q.equals("\\n")) return 10; if(q.equals("\\t")) return 9; return q.charAt(0);
        }
        try { return Integer.decode(s); } catch(Exception e) { error(line,"invalid immediate or label '"+s+"'"); return 0; }
    }
    static List<String> expand(String op,String[] a,int line) {
        List<String> r=new ArrayList<>();
        if(op.equals("li")) { if(a.length!=2) error(line,"li needs register, immediate"); int v=constant(a[1],Collections.emptyMap(),line); if(v>=-32768&&v<=65535) r.add("addiu "+a[0]+", $zero, "+v); else { r.add("lui "+a[0]+", "+((v>>>16)&65535)); r.add("ori "+a[0]+", "+a[0]+", "+(v&65535)); } }
        else if(op.equals("move")) { if(a.length!=2) error(line,"move needs two registers"); r.add("addu "+a[0]+", "+a[1]+", $zero"); }
        else if(op.equals("la")) { if(a.length!=2) error(line,"la needs register, label"); r.add("lui "+a[0]+", %hi("+a[1]+")"); r.add("ori "+a[0]+", "+a[0]+", %lo("+a[1]+")"); }
        else if(op.equals("rem")) { if(a.length!=3) error(line,"rem needs rd, rs, rt/immediate"); if(a[2].matches("-?\\d+")) r.add("addiu $at, $zero, "+a[2]); r.add("div "+a[1]+", "+(a[2].matches("-?\\d+")?"$at":a[2])); r.add("mfhi "+a[0]); }
        else if(op.equals("nop")) r.add("sll $zero, $zero, 0");
        else r.add(op+(a.length==0?"":" "+String.join(", ",a)));
        return r;
    }
    static int[] mem(String s,int line) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(-?(?:0x[0-9a-fA-F]+|\\d+))\\s*\\(([^()]+)\\)").matcher(s.trim());
        if(!m.matches()) { error(line,"expected offset(register)"); return null; } return new int[]{Integer.decode(m.group(1)),reg(m.group(2),line)};
    }
    static Result parse(String source,List<Item> items,Map<String,Integer> labels) {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); String section=""; int pc=TEXT_BASE;
        String[] lines=source.replace("\r"," ").split("\n");
        for(int lineNo=1;lineNo<=lines.length;lineNo++) {
            String s=noComment(lines[lineNo-1]); if(s.isEmpty()) continue;
            while(s.contains(":")) { int c=s.indexOf(':'); String name=s.substring(0,c).trim(); if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")) error(lineNo,"invalid label"); if(labels.containsKey(name)) error(lineNo,"duplicate label "+name); labels.put(name,section.equals("data")?DATA_BASE+data.size():pc); s=s.substring(c+1).trim(); if(s.isEmpty()) break; }
            if(s.isEmpty()) continue; String[] p=s.split("\\s+",2); String op=p[0].toLowerCase(); String tail=p.length>1?p[1]:"";
            if(op.equals(".text")||op.equals(".data")){section=op.substring(1);continue;} if(op.equals(".globl")||op.equals(".global"))continue;
            if(section.equals("data")) { if(op.equals(".asciiz")){ if(!tail.startsWith("\"")||!tail.endsWith("\""))error(lineNo,".asciiz requires quoted string"); String x=tail.substring(1,tail.length()-1).replace("\\n","\n").replace("\\t","\t").replace("\\\"","\""); try{data.write(x.getBytes(StandardCharsets.UTF_8));}catch(IOException e){throw new RuntimeException(e);} data.write(0); } else if(op.equals(".byte")){ for(String x:args(tail)){int v=constant(x,Collections.emptyMap(),lineNo);if(v<-128||v>255)error(lineNo,".byte out of range");data.write(v&255);} } else error(lineNo,"unsupported data directive "+op); continue; }
            if(!section.equals("text")) error(lineNo,"instruction outside .text");
            for(String e:expand(op,args(tail),lineNo)){items.add(new Item(pc,lineNo,e));pc+=4;}
        }
        return new Result(new ArrayList<>(),data.toByteArray());
    }
    static int encode(String text,int pc,int line,Map<String,Integer> labels) {
        String[] p=text.replace(","," ").trim().split("\\s+"); String op=p[0]; String[] a=Arrays.copyOfRange(p,1,p.length);
        java.util.function.Function<String,Integer> R=x->reg(x,line), I=x->constant(x,labels,line);
        if(op.equals("add")||op.equals("addu")||op.equals("sub")){int f=op.equals("add")?0x20:op.equals("addu")?0x21:0x22;return (R.apply(a[1])<<21)|(R.apply(a[2])<<16)|(R.apply(a[0])<<11)|f;}
        if(op.equals("addi")||op.equals("addiu")||op.equals("ori")){int v=op.equals("ori")&&a[2].startsWith("%lo(")?I.apply(a[2].substring(4,a[2].length()-1))&65535:I.apply(a[2]);if(v<-32768||v>65535)error(line,"immediate out of range");int c=op.equals("addi")?8:op.equals("addiu")?9:13;return(c<<26)|(R.apply(a[1])<<21)|(R.apply(a[0])<<16)|(v&65535);}
        if(op.equals("lui")){int v=a[1].startsWith("%hi(")?(I.apply(a[1].substring(4,a[1].length()-1))>>>16)&65535:I.apply(a[1]);return(15<<26)|(R.apply(a[0])<<16)|(v&65535);}
        if(op.equals("sll"))return(R.apply(a[1])<<16)|(R.apply(a[0])<<11)|(I.apply(a[2])<<6);
        if(op.equals("beq")||op.equals("bne")){int off=(I.apply(a[2])-(pc+4))/4;return(op.equals("beq")?4:5)<<26|R.apply(a[0])<<21|R.apply(a[1])<<16|(off&65535);}
        if(op.equals("j")||op.equals("jal"))return(op.equals("j")?2:3)<<26|(I.apply(a[0])>>>2&0x03ffffff);
        if(op.equals("jr"))return R.apply(a[0])<<21|8;
        if(op.equals("div"))return R.apply(a[0])<<21|R.apply(a[1])<<16|26;
        if(op.equals("mfhi"))return R.apply(a[0])<<11|16;
        if(op.equals("lw")||op.equals("sw")){int[]m=mem(a[1],line);return(op.equals("lw")?35:43)<<26|m[1]<<21|R.apply(a[0])<<16|(m[0]&65535);}
        if(op.equals("syscall"))return 12; error(line,"unsupported instruction "+op);return 0;
    }
    static void writeFiles(List<Integer>w,byte[]data,String base)throws IOException{
        try(PrintWriter out=new PrintWriter(base+".mhc-ascii")){out.println("MHC-ASCII 1\nBYTE_ORDER BIG_ENDIAN\nTEXT_BASE 0x00400000\nDATA_BASE 0x10010000\n.text");for(int x:w)out.println(String.format("%32s",Integer.toBinaryString(x)).replace(' ','0'));out.println(".data");for(byte b:data)out.println(String.format("%8s",Integer.toBinaryString(b&255)).replace(' ','0'));}
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(base+".mhc-bin"))){out.write(new byte[]{'M','H','C','B',1});out.writeInt(TEXT_BASE);out.writeInt(DATA_BASE);out.writeInt(w.size());for(int x:w)out.writeInt(x);out.writeInt(data.length);out.write(data);}
    }
    public static void main(String[] z)throws Exception{if(z.length!=1){System.err.println("usage: java Assembler fizzbuzz.asm");return;}String src=new String(Files.readAllBytes(Paths.get(z[0])),StandardCharsets.UTF_8);List<Item> items=new ArrayList<>();Map<String,Integer>labels=new LinkedHashMap<>();Result r=parse(src,items,labels);List<Integer>w=new ArrayList<>();for(Item i:items)w.add(encode(i.text,i.address,i.line,labels));writeFiles(w,r.data,z[0].replaceFirst("\\.[^.]+$",""));System.out.println("assembled "+w.size()+" text words and "+r.data.length+" data bytes");}
}
