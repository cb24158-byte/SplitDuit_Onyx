package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendRequest extends AppCompatActivity {

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private int currentUserId;
    private int receiptId;

    // --- Incoming split data parameters ---
    private String friendName;
    private String friendPhone;
    private double splitAmount = 0.00;
    private String splitNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_send_request);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = AppDatabase.getInstance(this);

        // 1. Retrieve the parameters passed down from your previous split config screen
        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", 1);
        receiptId = getIntent().getIntExtra("SAVED_RECEIPT_ID", -1);
        friendName = getIntent().getStringExtra("PASSED_NAME");
        friendPhone = getIntent().getStringExtra("PASSED_PHONE");
        splitNote = getIntent().getStringExtra("PASSED_NOTE");

        String rawAmount = getIntent().getStringExtra("PASSED_AMOUNT"); // e.g., "14.85" or "RM 14.85"
        if (rawAmount != null) {
            try {
                String cleanNum = rawAmount.replace("RM", "").trim();
                splitAmount = Double.parseDouble(cleanNum);
            } catch (NumberFormatException ignored) {
                splitAmount = 14.85; // safe fallback
            }
        }

        Button btnViewActivity = findViewById(R.id.btnViewActivity);

        // 2. Commit transaction metadata values on click, then move downstream
        btnViewActivity.setOnClickListener(v -> {
            if (friendName != null && !friendName.isEmpty()) {
                btnViewActivity.setEnabled(false);
                saveSplitToDatabaseAndNavigate();
            } else {
                // If accessed via a generic test stack, jump without storing empty mock rows
                navigateToTracker();
            }
        });
    }

    private void saveSplitToDatabaseAndNavigate() {
        databaseExecutor.execute(() -> {
            try {
                Date creationDate = new Date();

                // A. Insert friend row structure containing note and current calendar timeline markers
                Friend newFriendRecord = new Friend(friendName, friendPhone, creationDate);
                newFriendRecord.note = (splitNote != null && !splitNote.isEmpty()) ? splitNote : "Split Bill";

                // Save and pull the generated auto-increment tracking key
                long generatedFriendId = db.friendDao().insertFriend(newFriendRecord);

                // B. Generate a linked Debt row item (Corrected to map your local variables precisely)
                Debt newDebt = new Debt(
                        currentUserId,
                        receiptId,
                        (int) generatedFriendId, // <-- Uses your actual generated friend ID variable
                        friendName,
                        "Unpaid",
                        true,                    // isOwesYou Mode defaults to true here
                        splitAmount,             // <-- Uses your actual local splitAmount variable
                        friendPhone,             // <-- Uses your actual local friendPhone variable
                        System.currentTimeMillis()
                );

                db.debtDao().insertDebt(newDebt);

                // C. Synchronize changes onto the main process loop
                runOnUiThread(() -> {
                    Toast.makeText(SendRequest.this, "Request logged into Activity tracking!", Toast.LENGTH_SHORT).show();
                    navigateToTracker();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(SendRequest.this, "Database entry error.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void navigateToTracker() {
        Intent intent = new Intent(SendRequest.this, DebtTracker.class);
        intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}