package com.dsa.practice;

public class SumOfNumbersArray {
    public static void main(String[] args) {
        int a[]={1,2,3,4,5};
        System.out.println(sumNum(a,a.length-1));
    }

    public static int sumNum(int arr[], int n) {
        if (n == 0) {
            return arr[0];
        }
        return arr[n] + sumNum(arr,n-1);

    }
}
