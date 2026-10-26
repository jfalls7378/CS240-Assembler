import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Disassembles either MHC-ASCII or MHC-BIN produced by Assembler.java. */
public class Disassembler {
    static final int TEXT_BASE=0x00400000;
    static final String[] N={"zero","at","v0","v1","a0","a1","a2","a3","t0","t1","t2","t3","t4","t5","t6","t7","s0","s1","s2","s3","s4","s5","s6","s7","t8","t9","k0","k1","gp","sp","fp","ra"};
    static String r(int n){return "$"+N[n];}
    static class M {List<Integer>w;byte[]d;M(List<Integer>a,byte[]b){w=a;d=b;}}
    static M read(String file)throws Exception{
        if(file.endsWith(".mhc-ascii")){List<Integer>w=new ArrayList<>();ByteArrayOutputStream d=new ByteArrayOutputStream();boolean text=false,data=false;for(String s:Files.readAllLines(Paths.get(file))){s=s.trim();if(s.equals(".text")){text=true;data=false;}else if(s.equals(".data")){data=true;text=false;}else if(text&&s.matches("[01]{32}"))w.add((int)Long.parseLong(s,2));else if(data&&s.matches("[01]{8}"))d.write(Integer.parseInt(s,2));}return new M(w,d.toByteArray());}
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){byte[]m=new byte[5];in.readFully(m);if(m[0]!='M'||m[1]!='H'||m[2]!='C'||m[3]!='B'||m[4]!=1)throw new IOException("not an MHC binary file");in.readInt();in.readInt();int n=in.readInt();List<Integer>w=new ArrayList<>();for(int i=0;i<n;i++)w.add(in.readInt());int len=in.readInt();byte[]d=new byte[len];in.readFully(d);return new M(w,d);}
    }
    static String decode(int w,int pc){int op=w>>>26,rs=w>>>21&31,rt=w>>>16&31,rd=w>>>11&31,sh=w>>>6&31,fn=w&63,im=w&65535,sim=im>=32768?im-65536:im; if(op==0){if(fn==0)return"sll "+r(rd)+", "+r(rt)+", "+sh;if(fn==0x20)return"add "+r(rd)+", "+r(rs)+", "+r(rt);if(fn==0x21)return"addu "+r(rd)+", "+r(rs)+", "+r(rt);if(fn==0x22)return"sub "+r(rd)+", "+r(rs)+", "+r(rt);if(fn==8)return"jr "+r(rs);if(fn==12)return"syscall";if(fn==26)return"div "+r(rs)+", "+r(rt);if(fn==16)return"mfhi "+r(rd);}if(op==8||op==9||op==13)return(op==8?"addi":op==9?"addiu":"ori")+" "+r(rt)+", "+r(rs)+", "+(op==13?im:sim);if(op==15)return"lui "+r(rt)+", "+im;if(op==4||op==5)return(op==4?"beq ":"bne ")+r(rs)+", "+r(rt)+", 0x"+String.format("%08x",pc+4+(sim<<2));if(op==2||op==3)return(op==2?"j ":"jal ")+"0x"+String.format("%08x",(w&0x03ffffff)<<2);if(op==35||op==43)return(op==35?"lw ":"sw ")+r(rt)+", "+sim+"("+r(rs)+")";return".word 0x"+String.format("%08x",w);}
    static void write(M m,String out)throws Exception{Set<Integer>targets=new TreeSet<>();for(int i=0;i<m.w.size();i++){int w=m.w.get(i),pc=TEXT_BASE+i*4,op=w>>>26;if(op==2||op==3)targets.add((w&0x03ffffff)<<2);if(op==4||op==5){int x=w&65535;x=x>=32768?x-65536:x;targets.add(pc+4+(x<<2));}}Map<Integer,String>lab=new HashMap<>();int k=0;for(int x:targets)lab.put(x,"L"+k++);StringBuilder b=new StringBuilder(".data\ndata_bytes: .byte ");for(int i=0;i<m.d.length;i++){if(i>0)b.append(", ");b.append(m.d[i]&255);}b.append("\n.text\nmain:\n");for(int i=0;i<m.w.size();i++){int pc=TEXT_BASE+i*4;if(lab.containsKey(pc))b.append(lab.get(pc)).append(":\n");String s=decode(m.w.get(i),pc);for(Map.Entry<Integer,String>e:lab.entrySet())s=s.replace(String.format("0x%08x",e.getKey()),e.getValue());b.append("    ").append(s).append('\n');}Files.write(Paths.get(out),b.toString().getBytes(StandardCharsets.UTF_8));}
    public static void main(String[]z)throws Exception{if(z.length!=2){System.err.println("usage: java Disassembler input.mhc-ascii|input.mhc-bin output.asm");return;}M m=read(z[0]);write(m,z[1]);System.out.println("disassembled "+m.w.size()+" words and "+m.d.length+" data bytes");}
}

