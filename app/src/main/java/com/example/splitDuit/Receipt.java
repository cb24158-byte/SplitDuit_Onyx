package com.example.splitDuit;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "receipts",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "userId",
                childColumns = "userId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("userId")}
)
public class Receipt {
    @PrimaryKey(autoGenerate = true)
    public int receiptId;

    public int userId;
    public String shopName;
    public String item;
    public double price;
    public double totalPrice;

    public Receipt(int userId, String shopName, String item, double price, double totalPrice) {
        this.userId = userId;
        this.shopName = shopName;
        this.item = item;
        this.price = price;
        this.totalPrice = totalPrice;
    }

    public double getPrice() {
        return this.price;
    }

    public String getItem() {
        return this.item;
    }
}