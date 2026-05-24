package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private CardView cardScan, cardSplit, cardActivity;
    private TextView txtPendingAmount, txtActivityText, txtActivityAmount;
    private ImageButton btnLogout;

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Connect CardViews and Sub-Layout Elements
        cardScan = findViewById(R.id.cardScan);
        cardSplit = findViewById(R.id.cardSplit);
        cardActivity = findViewById(R.id.cardActivity);

        txtPendingAmount = findViewById(R.id.txtPendingAmount);
        txtActivityText = findViewById(R.id.txtActivityText);
        txtActivityAmount = findViewById(R.id.txtActivityAmount);

        btnLogout = findViewById(R.id.btnLogout);

        // 2. Room Database Instantiation Mapping
        db = AppDatabase.getInstance(this);

        currentUserId = getIntent().getIntExtra("LOGGED_IN_USER_ID", -1);
        if (currentUserId == -1) {
            currentUserId = 1; // Dev Fallback Token Instance ID
        }

        cardScan.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Opening Receipt Scanner...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, ScanReceipt.class);
            startActivity(intent);
        });

        // 4. Split Bill Navigation Path
        cardSplit.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ManualAmount.class);
            intent.putExtra("LOGGED_IN_USER_ID", currentUserId); // Add this line to secure your session ID tracking
            startActivity(intent);
        });

        // 5. Recent Activity Card Click Action Hook
        cardActivity.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DebtTracker.class);
            intent.putExtra("LOGGED_IN_USER_ID", currentUserId);
            startActivity(intent);
        });

        // 6. LOGOUT TERMINATION CONTROL ROUTINE
        btnLogout.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, LoginPage.class);
            startActivity(intent);
            finish(); // Destroys context page memory trace stacks completely
        });
    } // End of onCreate

    @Override
    protected void onResume() {
        super.onResume();
        // Load data onResume so that when you come back from adding a friend/debt,
        // the calculations update on screen immediately!
        loadDashboardData();
    }

    private void loadDashboardData() {
        databaseExecutor.execute(() -> {
            // A. Fetch all debts belonging to this user
            List<Debt> userDebts = db.debtDao().getAllDebtsForUser(currentUserId);

            // B. Fetch the absolute latest transaction entry
            Debt recentDebt = db.debtDao().getLatestDebtForUser(currentUserId);

            // C. Loop and calculate total balance calculations dynamically
            double runningSum = 0.0;
            for (Debt d : userDebts) {
                if (!"Paid".equalsIgnoreCase(d.status)) {
                    if (d.isOwesYou) {
                        runningSum += d.totalPrice; // Adds money they owe you (+)
                    } else {
                        runningSum -= d.totalPrice; // Subtracts money you owe them (-)
                    }
                }
            }

            final double balanceResult = runningSum;

            // D. UI Thread switch to safe presentation mode
            runOnUiThread(() -> {
                // Display proper signed values depending on positive vs negative debt values
                if (balanceResult >= 0) {
                    txtPendingAmount.setText(String.format(Locale.getDefault(), "RM %.2f", balanceResult));
                } else {
                    txtPendingAmount.setText(String.format(Locale.getDefault(), "-RM %.2f", Math.abs(balanceResult)));
                }

                // Render the single most recent activity record item dynamically
                if (recentDebt != null) {
                    cardActivity.setVisibility(View.VISIBLE);

                    if (recentDebt.isOwesYou) {
                        txtActivityText.setText(String.format("%s owes you for an item 💸", recentDebt.friendName));
                        txtActivityAmount.setText(String.format(Locale.getDefault(), "RM %.2f", recentDebt.totalPrice));
                    } else {
                        txtActivityText.setText(String.format("You owe %s for an item ", recentDebt.friendName));
                        txtActivityAmount.setText(String.format(Locale.getDefault(), "RM %.2f", recentDebt.totalPrice));
                    }
                } else {
                    // Hide recent activity block completely if database tables are bare
                    cardActivity.setVisibility(View.GONE);
                }
            });
        });
    }
}