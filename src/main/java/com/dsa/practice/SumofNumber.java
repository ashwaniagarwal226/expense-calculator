package com.dsa.practice;

//TODO optimise the solution for Integer.MAX_VALUE
public class SumofNumber {
    public static void main(String[] args) {
        System.out.println(recurSum(Integer.MAX_VALUE));
    }

    public static int recurSum(Integer n) {
        if (n == 0) {
            return 0;
        }
        return n + recurSum(n-1);
    }
}
