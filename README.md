# SplitDuit

## Overview

SplitDuit is an AI-powered bill splitting application that helps users split group expenses fairly, track unpaid balances, and send payment reminders automatically through WhatsApp.

## Problem Statement

Many people struggle to split bills accurately during group meals, trips, and shared expenses. Manual calculations can be time-consuming, error-prone, and often create awkward situations when reminding friends to pay back their share.

## Solution

SplitDuit simplifies expense sharing through:

* AI-powered receipt scanning
* Flexible bill splitting
* Debt tracking
* Automated WhatsApp payment reminders

---

## Technology Stack

### Mobile Application

* Android Studio
* Java
* XML

### Backend & Database

* Supabase Authentication
* Supabase PostgreSQL Database

### AI Integration

* Gemini API

### Communication Integration

* Meta WhatsApp API

---

## System Architecture

1. User creates or joins a bill.
2. Receipt is scanned using the device camera.
3. Gemini API extracts receipt items and prices.
4. Bill data is stored in Supabase.
5. Users split expenses equally or by item.
6. Outstanding balances are tracked automatically.
7. WhatsApp reminders are sent through the Meta WhatsApp API.

---

## Key Features

### AI Receipt Scanning

Automatically extracts receipt details and prices.

### Flexible Bill Splitting

Split bills equally or assign items individually.

### Debt Tracking

Monitor outstanding balances between users.

### WhatsApp Reminders

Automatically send payment reminders to debtors.

---

## Installation & Setup

### Prerequisites

* Android Studio
* Java JDK
* Supabase Project
* Gemini API Key
* Meta WhatsApp API Access

### Steps

1. Clone the repository:

```bash
git clone https://github.com/your-team/SplitDuit.git
```

2. Open the project in Android Studio.

3. Configure Supabase credentials.

4. Configure Gemini API key.

5. Configure Meta WhatsApp API credentials.

6. Build and run the application on an Android device or emulator.

---

## How the System is Built

SplitDuit follows a mobile-first architecture:

User → Android App → Supabase Database → Gemini API → Processed Results → WhatsApp Notifications

The application uses Supabase for authentication and data storage, Gemini API for receipt data extraction, and Meta WhatsApp API for automated payment reminders.

---

## AI Tools Used

The following AI tools were used during development:

* ChatGPT

  * Idea generation
  * Debugging assistance
  * Documentation support
  * UI/UX suggestions

* Gemini API

  * Receipt data extraction
  * AI-powered scanning functionality

All AI-generated outputs were reviewed, modified, and validated by the development team.

---

## Team ONYX

Hackathon X Fintech Forward 2026

SplitDuit – AI-Powered Bill Splitting Application
