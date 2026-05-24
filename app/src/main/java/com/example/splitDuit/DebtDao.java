package com.example.splitDuit;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface DebtDao {
    @Insert
    void insertDebt(Debt debt);

    @Update
    void updateDebt(Debt debt);

    @Query("SELECT * FROM debts WHERE userId = :userId")
    List<Debt> getAllDebtsForUser(int userId);

    @Query("SELECT * FROM debts WHERE isOwesYou = :isOwesYou")
    List<Debt> getDebtsByPerspective(boolean isOwesYou);

    @Query("SELECT * FROM debts WHERE isOwesYou = :isOwesYou AND friendName = :name LIMIT 1")
    Debt getDebtByName(boolean isOwesYou, String name);

    // --- ADD THIS METHOD TO FIX THE COMPILATION ERROR ---
    @Query("SELECT * FROM debts WHERE userId = :userId ORDER BY userId DESC LIMIT 1")
    Debt getLatestDebtForUser(int userId);
    @Query("SELECT friendPhoneNumber FROM friend WHERE friendName = :name LIMIT 1")
    String getPhoneNumberByFriendName(String name);
    @Query("SELECT * FROM debts WHERE isOwesYou = :isOwesYou")
    List<Debt> getAllDebtsByMode(boolean isOwesYou);
    @Query("SELECT * FROM debts WHERE isOwesYou = :isOwesYou")
    List<Debt> getDebts(boolean isOwesYou);

}