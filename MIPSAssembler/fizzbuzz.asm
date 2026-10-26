.data
fizz: .asciiz "Fizz"
buzz: .asciiz "Buzz"

.text
.globl main
main:
    li   $t1, 1
    li   $t2, 101
Loop:
    jal  PrintItem
    jal  PrintNewLine
    addi $t1, $t1, 1
    bne  $t1, $t2, Loop
    j    Exit

# Print Fizz, Buzz, FizzBuzz, or the number.
PrintItem:
    addi $sp, $sp, -8
    sw   $ra, 0($sp)
    rem  $t3, $t1, 3
    rem  $t4, $t1, 5
    bne  $t3, $zero, CheckBuzz
    jal  PrintFizz
    bne  $t4, $zero, PrintItemDone
    jal  PrintBuzz
    j    PrintItemDone
CheckBuzz:
    bne  $t4, $zero, CheckNumber
    jal  PrintBuzz
    j    PrintItemDone
CheckNumber:
    jal  PrintInt
PrintItemDone:
    lw   $ra, 0($sp)
    addi $sp, $sp, 8
    jr   $ra

PrintFizz:
    li   $v0, 4
    la   $a0, fizz
    syscall
    jr   $ra
PrintBuzz:
    li   $v0, 4
    la   $a0, buzz
    syscall
    jr   $ra
PrintInt:
    li   $v0, 1
    move $a0, $t1
    syscall
    jr   $ra
PrintNewLine:
    li   $v0, 11
    li   $a0, '\n'
    syscall
    jr   $ra
Exit:
    li   $v0, 10
    syscall
