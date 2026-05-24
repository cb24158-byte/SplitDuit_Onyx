package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Date;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddFriends extends AppCompatActivity {

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_friends);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = AppDatabase.getInstance(this);

        ImageButton btnBackAdd = findViewById(R.id.btnBackAdd);
        EditText etFriendName = findViewById(R.id.etFriendName);
        EditText etFriendPhone = findViewById(R.id.etFriendPhone);
        Button btnSaveFriend = findViewById(R.id.btnSaveFriend);

        btnBackAdd.setOnClickListener(v -> finish());

        btnSaveFriend.setOnClickListener(v -> {
            String name = etFriendName.getText().toString().trim();
            String phone = etFriendPhone.getText().toString().trim();

            if (!name.isEmpty() && !phone.isEmpty()) {
                databaseExecutor.execute(() -> {
                    Friend newFriend = new Friend(name, phone, new Date());
                    db.friendDao().insertFriend(newFriend);

                    runOnUiThread(() -> {

                        Toast.makeText(this, name + " saved successfully!", Toast.LENGTH_SHORT).show();

                        Intent returnIntent = new Intent();
                        returnIntent.putExtra("FRIEND_NAME", name);
                        setResult(RESULT_OK, returnIntent);

                        finish();
                    });
                });
            } else {
                Toast.makeText(this, "Please fill out all input fields", Toast.LENGTH_SHORT).show();
            }
        });
    }
}