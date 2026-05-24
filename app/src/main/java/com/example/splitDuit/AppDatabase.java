package com.example.splitDuit;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.Executors;
import androidx.annotation.NonNull;

@Database(entities = {User.class, Friend.class, Receipt.class, Debt.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract FriendDao friendDao();
    public abstract ReceiptDao receiptDao();
    public abstract DebtDao debtDao();

    private static AppDatabase instance;

    // We need to pass the context to the callback so it can access the database safely
    private static Context appContext;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            appContext = context.getApplicationContext(); // Save context for the callback

            instance = Room.databaseBuilder(appContext,
                            AppDatabase.class, "splitduit_database")
                    .fallbackToDestructiveMigration()
                    .addCallback(roomCallback)
                    .build();
        }
        return instance;
    }

    private static final RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            // Run on a background thread
            Executors.newSingleThreadExecutor().execute(() -> {
                // FIX: Get the database safely using the context instead of the null 'instance'
                AppDatabase tempDb = AppDatabase.getInstance(appContext);
                UserDao dao = tempDb.userDao();

                // Your hardcoded data
                dao.insertUser(new User("serene", "12345"));
                dao.insertUser(new User("nurin", "23456"));
            });
        }
    };
}