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

├── user/

│ ├── UserInfoHandler.java # Returns user profile information

│ └── UpdateUserHandler.java # Updates user profile information

├── certification/

│ ├── UpdateCertificationHandler.java # Updates certification/user_cert information

│ ├── GetCertificationHandler.java # Returns certification/user_cert information

│ ├── DeleteCertificationHandler.java # Deletes certification/user_cert information

│ └── CreateCertificationHandler.java # Creates certification/user_cert

├── utils/

│ ├── HashingHelper.java # Password hashing (SHA256)

│ └── JwtHelper.java # JWT signing & validation

| ……└── JwtValidationException # Custom JWT related exception

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

## Route JSON Body Formatting

### POST /certifications

To create a certification connected to a user (user_cert), you put its attributes in as so, keeping in mind that title, uid, and cert_level are NOT NULL. **Requires a JWT token in the Authentication header.** Also returns the ID of the user_cert: 

{

  "title": "Example Certification",
  
  "uid": "certification.example",
  
  "description": "This certification is an example",
  
  "cert_level": "intermediate",
  
  "earned_on": "2025-09-14",
  
  "expires_on": "2028-09-14",
  
  "ce_hours_required": 35,
  
  "ce_hours_completed": 35
  
}

### GET /certifications

Retrieving the info of all user_certs for a user is also possible by simply passing a user's JWT token through the Authorization header like so, you can optionally put a user_cert_id in the queryStringParameters to get only one user_cert:

{

  "headers": {
  
   "Authorization": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIiwidXNlcm5hbWUiOiJKb2VtYW1hIiwiaWF0IjoxNzU4NDg5NzA0LCJleHAiOjE3NTg0OTMzMDR9.OVSiTnU-1O400sJJD4U2tUCRytcnKdo5xmrIXyXbbIo"
    
  },"queryStringParameters": {
  
   "user_cert_id": "1"
    
  },
  
}

### PUT /certifications

To update a user_cert, you pass the "certification_id" attribute along with the attributes to be changed. **Requires a JWT token in the Authentication header.** Note that only the four attributes below can be changed after creation:

{
  
  "certification_id": 1,
  
  "earned_on": "2025-09-14",
  
  "expires_on": "2028-09-14",
  
  "ce_hours_required": 35,
  
  "ce_hours_completed": 35
  
}


### DELETE /certifications

Optionaly can take the user_cert_id of the user_cert to delete a specific user_cert, otherwise deletes all of a user's related rows. **Requires a JWT token in the Authentication header.** Returns number of rows deleted:

{

  "headers": {
  
   "Authorization": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIiwidXNlcm5hbWUiOiJKb2VtYW1hIiwiaWF0IjoxNzU4NDg5NzA0LCJleHAiOjE3NTg0OTMzMDR9.OVSiTnU-1O400sJJD4U2tUCRytcnKdo5xmrIXyXbbIo"
    
  },"queryStringParameters": {
  
   "user_cert_id": "1"
    
  },
  
}

### POST /register

Registers a use but also logs them in, returning a JWT token to be stored by the frontend.

{

  "first_name": "Joe",
  
  "last_name": "Mama",
  
  "username": "Joemama",
  
  "password": "daPassword",
  
  "industry" : "exampleIndustry",
  
  "user_role" : "exampleRole",
  
  "bio" : "please work"
  
}

### POST /login

Requires only the username and password, returning a JWT if they match:

{

  "username": "Joemama",
  
  "password": "daPassword"
  
}

### GET /user

Returns all of the app_user table. Simply pass a user's token through the Authentication header and it will return all their info like so:

{

   "headers": {
   
   "Authorization": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIiwidXNlcm5hbWUiOiJKb2VtYW1hIiwiaWF0IjoxNzU4NDg5NzA0LCJleHAiOjE3NTg0OTMzMDR9.OVSiTnU-1O400sJJD4U2tUCRytcnKdo5xmrIXyXbbIo"
  
  }
  
}

### PUT /user

Updates user profile information, not including the username and password. Returns the updated user information:

{

  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzIiwidXNlcm5hbWUiOiJKb2VtYW1hIiwiaWF0IjoxNzU4MjMwODgzLCJleHAiOjE3NTgyMzQ0ODN9.qIBzPbpXPmh2Jughej4wwRzxQ9HsTgSYPNz6vi3lcug",
  
  "first_name": "Joe",
  
  "last_name": "Mama",
  
  "username": "Joemama",
  
  "password": "daPassword",
  
  "industry" : "exampleIndustry",
  
  "user_role" : "exampleRole",
  
  "bio" : "This should be updated!"
  
}


---

## HTTP Status Codes

* 200 – Successful action

* 201 – Resource created successfully

* 400 - Missing/Unknown

* 401 – Unauthorized (expired token or invalid credentials)

* 403 - Forbidden (invalid token)

* 404 - User not found

* 409 – Conflict (username already exists)

* 500 – Internal server error
