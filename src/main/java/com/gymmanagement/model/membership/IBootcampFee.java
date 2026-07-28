package com.gymmanagement.model.membership;

public interface IBootcampFee {

    double BASE_FEE      = 35.50;
    double DISCOUNT_RATE = 0.07;

    double calcBootcampFee(int classesEnrolled);
    double applyDiscount(double originalFee);
}
