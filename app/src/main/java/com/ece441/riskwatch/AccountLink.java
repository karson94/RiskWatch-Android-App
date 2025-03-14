package com.ece441.riskwatch;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AccountLink extends AppCompatActivity {

    private EditText userIdInput;
    private TextView currentUserIdText;

    private TextView linkedUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_link);

        userIdInput = findViewById(R.id.userIdInput);
        currentUserIdText = findViewById(R.id.currentUserIdText);

        // Display the current user's ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String currentUserId = currentUser.getUid();
            currentUserIdText.setText("Your User ID: " + currentUserId);
        } else {
            currentUserIdText.setText("User ID not available");
        }

//        fetchLinkedUserDisplayName();

    }

    public void linkAccount(View view) {
        String userId = userIdInput.getText().toString().trim();

        // Validate input
        if (userId.isEmpty()) {
            Toast.makeText(this, "Please enter a user ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Proceed with linking process
        grantPermissionToUser(userId);
    }

    private void grantPermissionToUser(String userId) {
        // Get the current user's ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, handle this case
            return;
        }
        String ownerId = currentUser.getUid();

        // Get reference to the permissions node in the database
        DatabaseReference permissionsRef = FirebaseDatabase.getInstance().getReference("permissions")
                .child(ownerId).child("grantedUsers").child(userId);

        // Update the permissions node to grant access to the specified user ID
        permissionsRef.setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(AccountLink.this, "Account linked successfully", Toast.LENGTH_SHORT).show();
                    finish(); // Close the activity after linking
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AccountLink.this, "Failed to link account: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    public void goBackToHome(View view) {
        onBackPressed();
    }

    public void copyUserId(View view) {
        String userIdText  = currentUserIdText.getText().toString();
        String userId = userIdText.substring(userIdText.lastIndexOf(":") + 1).trim();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("User ID", userId);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "User ID copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
}