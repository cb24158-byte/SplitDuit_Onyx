package com.example.splitDuit;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginPage extends AppCompatActivity {

    EditText edtUsername;
    EditText edtPassword;
    Button btnLogin;

    private AppDatabase db;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_page);

        // Initialize Database
        db = AppDatabase.getInstance(this);

        databaseExecutor.execute(() -> {
            UserDao dao = db.userDao();
            // Check if "serene" exists; if not, inject her and nurin!
            if (dao.login("serene", "12345") == null) {
                dao.insertUser(new User("serene", "12345"));
                dao.insertUser(new User("nurin", "23456"));
            }
        });

        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> loginUser());
    }

    private void loginUser() {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (username.isEmpty()) {
            edtUsername.setError("Enter username");
            edtUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            edtPassword.setError("Enter password");
            edtPassword.requestFocus();
            return;
        }

        // Disable button while checking database
        btnLogin.setEnabled(false);
        btnLogin.setText("Checking...");

        // Query the database on a background thread
        databaseExecutor.execute(() -> {
            User loggedInUser = db.userDao().login(username, password);

            runOnUiThread(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");

                if (loggedInUser != null) {
                    // Database found the user!
                    Toast.makeText(this, "Welcome back, " + loggedInUser.username + "!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginPage.this, MainActivity.class);
                    // Dynamically pass whatever database ID was matched!
                    intent.putExtra("LOGGED_IN_USER_ID", loggedInUser.userId);
                    startActivity(intent);
                    finish();
                } else {
                    // Database did not find the user
                    Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}