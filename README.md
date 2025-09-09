# study-buddy-backend
The backend of the **Study Buddy** application provides user authentication, profile management, and certification tracking.  
It is built as a collection of **AWS Lambda functions**, using **Java 21**, **PostgreSQL (Amazon RDS)**, and **JWT-based authentication**.

---

## Features

- **User Registration** – Create a new account with profile details.
- **User Login** – Authenticate with username/password and receive a signed JWT.
- **JWT-based Authentication** – Securely manage sessions without server-side session storage.
- **Profile & Certifications** – Associate user actions (like adding certifications) with the correct user via `user_id` in the JWT.
- **Database Integration** – PostgreSQL on Amazon RDS stores user and certification data.

---

##  Tech Stack

- **Java 21**
- **AWS Lambda** (deployed via AWS Console)
- **PostgreSQL (Amazon RDS)**
- **Maven** (build and dependency management)
- **io.jsonwebtoken (JJWT)** for JWT signing & validation
- **Deployed Frontend**: Vercel (communicates with backend Lambdas)

---

## Project Structure

src/

├── authenticate/

│ └── LoginHandler.java # User login + JWT generation

├── register/

│ └── RegisterUserHandler.java # User registration + JWT generation

├── utils/

│ ├── HashingHelper.java # Password hashing (SHA256)

│ └── JwtHelper.java # JWT signing & validation (*IN DEVELOPMENT*)

└── … (future)


---

## Authentication Flow

1. **Register User**  
   - Frontend sends `POST /register` with `first_name`, `last_name`, `username`, `password`, `industry`, `user_role`, `bio`.
   - Backend inserts user into DB, retrieves `user_id`, and returns a JWT containing that `user_id`.  

2. **Login User**  
   - Frontend sends `POST /login` with `username`, `password`.  
   - Backend validates password, retrieves `user_id`, and returns a JWT.  

3. **Authorized Requests**  
   - Frontend includes `Authorization: Bearer <token>` header in subsequent requests.  
   - Backend verifies JWT with the shared secret key (`JWT_KEY`), extracts `user_id`, and performs DB operations for the correct user.  

---

## Environment Variables

The backend relies on the following environment variables (set in AWS Lambda):

| Variable       | Description                          |
|----------------|--------------------------------------|
| `DB_URL`       | JDBC URL for RDS PostgreSQL instance |
| `DB_USER`      | Database username                    |
| `DB_PASSWORD`  | Database password                    |
| `JWT_KEY`      | Secret key used to sign JWTs (must be 256-bit for HS256) |

---

## Local Development

1. Ensure you have **Java 21** and **Maven** installed.
2. Ensure the remote PostgreSQL database is available with necessary schema.
3. Run `mvn clean package shade:shade` in the location of the `pom.xml` file to build.
4. Locate jar files under target folder.
5. Deploy the SHADED jar file to Lambda **(name-version-SNAPSHOT-shaded).**
6. Ensure functionality with Lambda test event.

---

## Verifying JWT in Other Lambdas

To protect a Lambda function:

import io.jsonwebtoken.*;

Claims claims = JwtHelper.verifyToken(jwt, System.getenv("JWT_KEY"));

int userId = claims.get("user_id", Integer.class);

// Use userId to query or update database rows

If verification fails, return 401 Unauthorized.

---

## Frontend Integration (Vercel)

After login or registration, the frontend stores the returned token (e.g. in localStorage).

All subsequent API calls include:

Authorization: Bearer <token>


Backend Lambdas use the token to identify the correct user without exposing passwords.

---

## HTTP Status Codes

* 201 – User created successfully

* 200 – Login successful

* 401 – Unauthorized (invalid credentials or token)

* 404 - User not found

* 409 – Conflict (username already exists)

* 500 – Internal server error
