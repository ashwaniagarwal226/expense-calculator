package com.expense.calculator.utils;

import com.expense.calculator.hdfc.DTOs.TransactionType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionUtils {
    //TODO fix this as its a bad design
    public static String getTransactionType (String transactionName){
        if(containsWholeWord(transactionName,"HERE SOLUTIONS")){
            return TransactionType.SALARY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"RENT")){
            return TransactionType.RENT.getTransactionType();
        }
        //RENTOMOJO
        if(containsWholeWord(transactionName,"EDUNETWORK")){
            return TransactionType.RENT.getTransactionType();
        }
        //CITYFURNISH
        if(containsWholeWord(transactionName,"CITYFURNISH")){
            return TransactionType.RENT.getTransactionType();
        }
        if(containsWholeWord(transactionName,"SWIGGY")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"SWIGGYSTORES")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        //SWIGGY
        if(containsWholeWord(transactionName,"BUNDL TECHNOLOGIES")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"CHAMPA DEVI")){
            return TransactionType.PARENTS.getTransactionType();
        }
        if(containsWholeWord(transactionName,"HATHWAY")){
            return TransactionType.INTERNET.getTransactionType();
        }
        if(containsWholeWord(transactionName,"JIOFIBER")){
            return TransactionType.INTERNET.getTransactionType();
        }
        if(containsWholeWord(transactionName,"SHAILAJA")){
            return TransactionType.MAID.getTransactionType();
        }
        //BLINKIT
        if(containsWholeWord(transactionName,"GROFERS")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        //BLINKIT
        if(containsWholeWord(transactionName,"BLINKIT")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        //ZEPTO
        if(containsWholeWord(transactionName,"GEDDIT")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        //ZEPTO
        if(containsWholeWord(transactionName,"ZEPTONOW")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"ZOMATO")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"ZOMATOCOM")){
            return TransactionType.SWIGGY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"SWIGGY DINEOUT")){
            return TransactionType.DINNER.getTransactionType();
        }
        if(containsWholeWord(transactionName,"TAXI")){
            return TransactionType.CAB_SERVICE.getTransactionType();
        }
        if(containsWholeWord(transactionName,"UBER")){
            return TransactionType.CAB_SERVICE.getTransactionType();
        }
        if(containsWholeWord(transactionName,"OLACABS")){
            return TransactionType.CAB_SERVICE.getTransactionType();
        }
        if(containsWholeWord(transactionName,"INTEREST")){
            return TransactionType.INTEREST.getTransactionType();
        }
        if(containsWholeWord(transactionName,"AMAZON")){
            return TransactionType.SHOPPING.getTransactionType();
        }
        if(containsWholeWord(transactionName,"MYNTRA")){
            return TransactionType.SHOPPING.getTransactionType();
        }
        if(containsWholeWord(transactionName,"URBANCOMPANY")){
            return TransactionType.URBANCOMPANY.getTransactionType();
        }
        if(containsWholeWord(transactionName,"NETFLIX")){
            return TransactionType.SUBSCRIPTIONS.getTransactionType();
        }
        if(containsWholeWord(transactionName,"APPLE")){
            return TransactionType.SUBSCRIPTIONS.getTransactionType();
        }
        if(containsWholeWord(transactionName,"APPLE SERVICES")){
            return TransactionType.SUBSCRIPTIONS.getTransactionType();
        }
        if(containsWholeWord(transactionName,"GULP")){
            return TransactionType.LIQUOR.getTransactionType();
        }
        if(containsWholeWord(transactionName,"TOIT")){
            return TransactionType.LIQUOR.getTransactionType();
        }
        return "OTHERS";
    }

    public static boolean containsWholeWord(String input,String word) {
        String regex = "\\b(?i)"+word+"\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);

        return matcher.find();
    }
}
