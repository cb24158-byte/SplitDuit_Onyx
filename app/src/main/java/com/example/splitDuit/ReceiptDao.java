package com.example.splitDuit;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ReceiptDao {
    @Insert
    long insertReceipt(Receipt receipt);
    @Query("SELECT * FROM receipts WHERE userId = :userId AND totalPrice = (SELECT totalPrice FROM receipts WHERE receiptId = :receiptId)")
    List<Receipt> getAllItemsInReceiptSession(int userId, int receiptId);

    @Query("SELECT * FROM receipts WHERE userId = :userId")
    List<Receipt> getAllReceiptsForUser(int userId);

    @Delete
    void deleteReceipt(Receipt receipt);
}