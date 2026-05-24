package com.example.splitDuit;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "debts",
        foreignKeys = {
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "userId",
                        childColumns = "userId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Receipt.class,
                        parentColumns = "receiptId",
                        childColumns = "receiptId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Friend.class,
                        parentColumns = "friendId",
                        childColumns = "friendId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("userId"), @Index("receiptId"), @Index("friendId")}
)
public class Debt {
    @PrimaryKey(autoGenerate = true)
    public int debtId;

    public int userId;
    public int receiptId;
    public int friendId;
    public String friendName;
    public String friendPhoneNumber;
    public String status;
    public boolean isOwesYou;
    public double totalPrice;
    public long dueDateTimestamp; // <-- NEW FIELD FOR DATES

    // Updated Constructor
    public Debt(int userId, int receiptId, int friendId, String friendName, String status,
                boolean isOwesYou, double totalPrice, String friendPhoneNumber, long dueDateTimestamp) {
        this.userId = userId;
        this.receiptId = receiptId;
        this.friendId = friendId;
        this.friendName = friendName;
        this.status = status;
        this.isOwesYou = isOwesYou;
        this.totalPrice = totalPrice;
        this.friendPhoneNumber = friendPhoneNumber;
        this.dueDateTimestamp = dueDateTimestamp;
    }
}