package com.attendance.models;

public class Student {
    private final String rollNo;
    private final String name;
    private final String className;

    public Student(String rollNo, String name, String className) {
        this.rollNo = rollNo;
        this.name = name;
        this.className = className;
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return rollNo + " - " + name + " (" + className + ")";
    }
}