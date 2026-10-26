.data
data_bytes: .byte 70, 105, 122, 122, 0, 66, 117, 122, 122, 0
.text
main:
    addiu $t1, $zero, 1
    addiu $t2, $zero, 101
L0:
    jal L1
    jal L8
    addi $t1, $t1, 1
    bne $t1, $t2, L0
    j L9
L1:
    addi $sp, $sp, -8
    sw $ra, 0($sp)
    addiu $at, $zero, 3
    div $t1, $at
    mfhi $t3
    addiu $at, $zero, 5
    div $t1, $at
    mfhi $t4
    bne $t3, $zero, L2
    jal L5
    bne $t4, $zero, L4
    jal L6
    j L4
L2:
    bne $t4, $zero, L3
    jal L6
    j L4
L3:
    jal L7
L4:
    lw $ra, 0($sp)
    addi $sp, $sp, 8
    jr $ra
L5:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 0
    syscall
    jr $ra
L6:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 5
    syscall
    jr $ra
L7:
    addiu $v0, $zero, 1
    addu $a0, $t1, $zero
    syscall
    jr $ra
L8:
    addiu $v0, $zero, 11
    addiu $a0, $zero, 10
    syscall
    jr $ra
L9:
    addiu $v0, $zero, 10
    syscall
