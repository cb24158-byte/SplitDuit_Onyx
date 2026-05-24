package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DebtTracker extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroup;
    private TextView txtNameOverdue, txtDetailOverdue, txtStatusOverdue;
    private TextView txtNamePending, txtDetailPending, txtStatusPending;
    private TextView txtNamePaid, txtDetailPaid;
    private TextView txtNameOverduePaid, txtNamePendingPaid;
    private TextView txtEmptyOverdue, txtEmptyPending;
    private MaterialCardView cardOverdue, cardPending, cardPaid, cardOverduePaidCopy, cardPendingPaidCopy;

    private boolean isOwesYouMode = true;
    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> reminderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String returnedName = data.getStringExtra("PASSED_NAME");
                    boolean isPaid = data.getBooleanExtra("IS_PAID", false);

                    if (isPaid && returnedName != null) {
                        databaseExecutor.execute(() -> {
                            Debt debtRecord = db.debtDao().getDebtByName(isOwesYouMode, returnedName);
                            if (debtRecord != null) {
                                debtRecord.status = "Paid";
                                db.debtDao().updateDebt(debtRecord);
                                runOnUiThread(this::updateUI);
                            }
                        });
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_debt_tracker);

        // Initialize UI Elements
        toggleGroup = findViewById(R.id.toggleGroup);

        txtNameOverdue = findViewById(R.id.txtNameOverdue);
        txtDetailOverdue = findViewById(R.id.txtDetailOverdue);
        txtStatusOverdue = findViewById(R.id.txtStatusOverdue);

        txtNamePending = findViewById(R.id.txtNamePending);
        txtDetailPending = findViewById(R.id.txtDetailPending);
        txtStatusPending = findViewById(R.id.txtStatusPending);

        txtNamePaid = findViewById(R.id.txtNamePaid);
        txtDetailPaid = findViewById(R.id.txtDetailPaid);

        txtNameOverduePaid = findViewById(R.id.txtNameOverduePaid);
        txtNamePendingPaid = findViewById(R.id.txtNamePendingPaid);

        txtEmptyOverdue = findViewById(R.id.txtEmptyOverdue);
        txtEmptyPending = findViewById(R.id.txtEmptyPending);

        cardOverdue = findViewById(R.id.cardOverdue);
        cardPending = findViewById(R.id.cardPending);
        cardPaid = findViewById(R.id.cardPaid);
        cardOverduePaidCopy = findViewById(R.id.cardOverduePaidCopy);
        cardPendingPaidCopy = findViewById(R.id.cardPendingPaidCopy);

        db = AppDatabase.getInstance(this);

        // SEED THE DATABASE WITH YOUR 12 HARDCODED SCENARIOS PERMANENTLY
        databaseExecutor.execute(() -> {
            if (db.debtDao().getAllDebtsForUser(1).isEmpty()) {
                long now = System.currentTimeMillis();
                long fourDaysAgo = now - TimeUnit.DAYS.toMillis(4);
                long twoDaysAgo = now - TimeUnit.DAYS.toMillis(2);
                long tomorrow = now + TimeUnit.DAYS.toMillis(1);

                // --- OWES YOU PERSPECTIVE (isOwesYouMode = true) ---
                db.debtDao().insertDebt(new Debt(1, 1, 1, "Ali", "Unpaid", true, 20.45, "601140528854", fourDaysAgo));
                db.debtDao().insertDebt(new Debt(1, 1, 2, "Abu", "Unpaid", true, 94.35, "60182041535", tomorrow));
                db.debtDao().insertDebt(new Debt(1, 1, 3, "Cheong", "Paid", true, 13.40, null, now));

                // --- YOU OWE PERSPECTIVE (isOwesYouMode = false) ---
                db.debtDao().insertDebt(new Debt(1, 1, 4, "Kim", "Unpaid", false, 25.50, "601140528854", twoDaysAgo));
                db.debtDao().insertDebt(new Debt(1, 1, 5, "Sarah", "Unpaid", false, 10.00, "60182041535", tomorrow));
                db.debtDao().insertDebt(new Debt(1, 1, 6, "Mira", "Paid", false, 50.00, null, now));
            }
            runOnUiThread(this::updateUI);
        });

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isOwesYouMode = (checkedId == R.id.btnOwesYou);
                updateUI();
            }
        });

        cardOverdue.setOnClickListener(v -> handleCardClick(txtNameOverdue.getText().toString(), "Overdue"));
        cardPending.setOnClickListener(v -> handleCardClick(txtNamePending.getText().toString(), "Pending"));
        cardPaid.setOnClickListener(v -> Toast.makeText(this, "This transaction is fully settled.", Toast.LENGTH_SHORT).show());
    }

    private void handleCardClick(String friendName, String expectedStatus) {
        if (friendName.isEmpty()) return;
        databaseExecutor.execute(() -> {
            Debt selectedDebt = db.debtDao().getDebtByName(isOwesYouMode, friendName);
            runOnUiThread(() -> {
                String currentStatus = (selectedDebt != null) ? selectedDebt.status : expectedStatus;
                double amount = (selectedDebt != null) ? selectedDebt.totalPrice : 0.0;

                if (isOwesYouMode) {
                    if ("Paid".equals(currentStatus)) {
                        Toast.makeText(this, friendName + " has already paid!", Toast.LENGTH_SHORT).show();
                    } else {
                        openReminderActivity(friendName, "RM " + amount);
                    }
                } else {
                    if (!"Paid".equals(currentStatus)) {
                        Toast.makeText(this, "Return " + friendName + " money!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "You already settled this debt!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    private void openReminderActivity(String name, String formattedAmount) {
        Intent intent = new Intent(DebtTracker.this, ReminderActivity.class);
        intent.putExtra("NAME", name);
        intent.putExtra("AMOUNT", formattedAmount);
        intent.putExtra("IS_OWES_YOU_MODE", isOwesYouMode);
        reminderLauncher.launch(intent);
    }

    private void updateUI() {
        if (db == null) return;

        databaseExecutor.execute(() -> {
            // Target the dynamic seeded database names
            final Debt overdue = db.debtDao().getDebtByName(isOwesYouMode, isOwesYouMode ? "Ali" : "Kim");
            final Debt pending = db.debtDao().getDebtByName(isOwesYouMode, isOwesYouMode ? "Abu" : "Sarah");
            final Debt paid = db.debtDao().getDebtByName(isOwesYouMode, isOwesYouMode ? "Cheong" : "Mira");

            runOnUiThread(() -> {
                // 1. Process Overdue Layout Cards
                if (overdue != null) {
                    txtNameOverdue.setText(overdue.friendName);
                    txtNameOverduePaid.setText(overdue.friendName);

                    if ("Paid".equals(overdue.status)) {
                        cardOverdue.setVisibility(View.GONE);
                        cardOverduePaidCopy.setVisibility(View.VISIBLE);
                    } else {
                        cardOverdue.setVisibility(View.VISIBLE);
                        cardOverduePaidCopy.setVisibility(View.GONE);

                        txtDetailOverdue.setText(isOwesYouMode ? "Owes you RM " + overdue.totalPrice : "You owe RM " + overdue.totalPrice);
                        txtStatusOverdue.setText(getDaysCalculationFromDate(overdue.dueDateTimestamp));
                    }
                } else {
                    cardOverdue.setVisibility(View.GONE);
                    cardOverduePaidCopy.setVisibility(View.GONE);
                }

                // 2. Process Pending Layout Cards
                if (pending != null) {
                    txtNamePending.setText(pending.friendName);
                    txtNamePendingPaid.setText(pending.friendName);

                    if ("Paid".equals(pending.status)) {
                        cardPending.setVisibility(View.GONE);
                        cardPendingPaidCopy.setVisibility(View.VISIBLE);
                    } else {
                        cardPending.setVisibility(View.VISIBLE);
                        cardPendingPaidCopy.setVisibility(View.GONE);

                        txtDetailPending.setText(isOwesYouMode ? "Owes you RM " + pending.totalPrice : "You owe RM " + pending.totalPrice);
                        txtStatusPending.setText(getDaysCalculationFromDate(pending.dueDateTimestamp));
                    }
                } else {
                    cardPending.setVisibility(View.GONE);
                    cardPendingPaidCopy.setVisibility(View.GONE);
                }

                // 3. Process Paid Layout Cards
                if (paid != null) {
                    cardPaid.setVisibility(View.VISIBLE);
                    txtNamePaid.setText(paid.friendName);
                    txtDetailPaid.setText("Paid RM " + paid.totalPrice);
                } else {
                    cardPaid.setVisibility(View.GONE);
                }

                // 4. Update Blank/Empty Section Visibility States
                boolean noActiveOverdue = (overdue == null || "Paid".equals(overdue.status));
                boolean noActivePending = (pending == null || "Paid".equals(pending.status));

                txtEmptyOverdue.setVisibility(noActiveOverdue ? View.VISIBLE : View.GONE);
                txtEmptyOverdue.setText(isOwesYouMode ? "No one owes you money (Overdue)" : "You don't owe anyone money (Overdue)");

                txtEmptyPending.setVisibility(noActivePending ? View.VISIBLE : View.GONE);
                txtEmptyPending.setText(isOwesYouMode ? "No pending transactions" : "No pending bills to pay");
            });
        });
    }

    private String getDaysCalculationFromDate(Long targetDueDateTimestamp) {
        if (targetDueDateTimestamp == null) return "";

        long currentTime = System.currentTimeMillis();
        long differenceInMillis = targetDueDateTimestamp - currentTime;

        long differenceInDays = Math.round((double) differenceInMillis / (1000 * 60 * 60 * 24));

        if (differenceInDays < 0) {
            long absoluteDays = Math.abs(differenceInDays);
            return absoluteDays + (absoluteDays == 1 ? " day late" : " days late");
        } else if (differenceInDays == 0) {
            return "Due today";
        } else if (differenceInDays == 1) {
            return "Due tomorrow";
        } else {
            return "Due in " + differenceInDays + " days";
        }
    }
}