package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        printCommands();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            boolean reprintCommands = true;
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
                reprintCommands = false;
            }
            if (reprintCommands) {
                System.out.println();
                printCommands();
            }
        }
    }

    private static void printCommands() {
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");
        System.out.println("> reserve <date> <vaccine>");
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");
        System.out.println("> logout");
        System.out.println("> quit");
        System.out.println();
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        // check 1: length of tokens
        if (tokens.length != 3) {
            System.out.println("Create patient failed");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];
        // check 2: strong pwd
        if (!isStrongPassword(password)) {
            System.out.println("Create patient failed");
            return;
        }

        // check 3: check whether username already exists
        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again");
            return;
        }

        // create the patient
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Create patient failed");
            e.printStackTrace();
        }
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        if (!isStrongPassword(password)) {
            System.out.println("Please use a strong password");
            return;
        }
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";

        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in, try again");
            return;
        }

        // check 2: the length for tokens
        if (tokens.length != 3) {
            System.out.println("Login patient failed");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }

        // check if the login was successful
        if (patient == null) {
            System.out.println("Login patient failed.");
        } else {
            currentPatient = patient;
            System.out.println("Logged in as: " + username);
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // search_caregiver_schedule <date>
        // check 1: login
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }

        // check 2: token length
        if (tokens.length != 2) {
            System.out.println("Please try again");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String date = tokens[1];

        try {
            Date d = Date.valueOf(date);

            // get the data of caregivers
            String searchSchedule = "SELECT username FROM Availabilities WHERE time = ? ORDER BY username";
            PreparedStatement statement = con.prepareStatement(searchSchedule);
            statement.setDate(1, d);
            ResultSet resultSet = statement.executeQuery();

            // get all the name of caregivers
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                System.out.println(username);
            }

            // get the data of vaccines
            String searchVaccine = "SELECT name, doses FROM Vaccines WHERE doses > 0 ";
            statement = con.prepareStatement(searchVaccine);
            resultSet = statement.executeQuery();

            // get all the name of vaccines
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                int doses = resultSet.getInt("doses");
                System.out.println(name + " " + doses);
            }
        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void reserve(String[] tokens) {
        // reserve <date> <vaccine>
        // check 1: login
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }

        // check 2: login as patient
        if (currentPatient == null) {
            System.out.println("Please login as a patient");
            return;
        }

        // check 3: token length
        if (tokens.length != 3) {
            System.out.println("Please try again");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String date = tokens[1];
        String vaccineName = tokens[2];

        try {
            Date d = Date.valueOf(date);

            // check 4: caregivers and conflicts
            String caregiverQuery = "SELECT username FROM Availabilities WHERE time = ? ORDER BY username";
            PreparedStatement caregiverStatement = con.prepareStatement(caregiverQuery);
            caregiverStatement.setDate(1, d);
            ResultSet caregiverResult = caregiverStatement.executeQuery();

            String caregiverName = null;
            while (caregiverResult.next()) {
                String potentialCaregiver = caregiverResult.getString("username");
                String conflictQuery = "SELECT * FROM Reservations WHERE Time = ? AND CaregiverName = ?";
                PreparedStatement conflictStatement = con.prepareStatement(conflictQuery);
                conflictStatement.setDate(1, d);
                conflictStatement.setString(2, potentialCaregiver);
                ResultSet conflictResult = conflictStatement.executeQuery();
                if (!conflictResult.next()) {
                    caregiverName = potentialCaregiver;
                    break;
                }
            }
            if (caregiverName == null) {
                System.out.println("No caregiver is available");
                return;
            }

            // check 5: vaccine
            String vaccineQuery = "SELECT doses FROM Vaccines WHERE name = ?";
            PreparedStatement vaccineStatement = con.prepareStatement(vaccineQuery);
            vaccineStatement.setString(1, vaccineName);
            ResultSet vaccineResult = vaccineStatement.executeQuery();
            if (!vaccineResult.next()) {
                System.out.println("Please try again");
                return;
            }
            int availableDoses = vaccineResult.getInt("doses");
            if (availableDoses <= 0) {
                System.out.println("Not enough available doses");
                return;
            }

            // Make reservation
            String reservationQuery = "INSERT INTO Reservations (Time, CaregiverName, VaccineName, PatientName) VALUES (?, ?, ?, ?)";
            PreparedStatement reservationStatement = con.prepareStatement(reservationQuery);
            reservationStatement.setDate(1, d);
            reservationStatement.setString(2, caregiverName);
            reservationStatement.setString(3, vaccineName);
            reservationStatement.setString(4, currentPatient.getUsername());
            reservationStatement.executeUpdate();

            // Retrieve appointmentID
            String getAppointmentIdQuery = "SELECT ID FROM Reservations WHERE Time = ? AND CaregiverName = ? AND VaccineName = ? AND PatientName = ?";
            PreparedStatement getAppointmentIdStatement = con.prepareStatement(getAppointmentIdQuery);
            getAppointmentIdStatement.setDate(1, d);
            getAppointmentIdStatement.setString(2, caregiverName);
            getAppointmentIdStatement.setString(3, vaccineName);
            getAppointmentIdStatement.setString(4, currentPatient.getUsername());
            ResultSet appointmentIdResult = getAppointmentIdStatement.executeQuery();
            int appointmentId = -1;
            if (appointmentIdResult.next()) {
                appointmentId = appointmentIdResult.getInt("ID");
            } else {
                System.out.println("Please try again");
            }

            // Update doses
            String updateVaccineQuery = "UPDATE Vaccines SET doses = doses - 1 WHERE name = ?";
            PreparedStatement updateVaccineStatement = con.prepareStatement(updateVaccineQuery);
            updateVaccineStatement.setString(1, vaccineName);
            updateVaccineStatement.executeUpdate();

            // Remove caregiver availability
            String deleteAvailabilityQuery = "DELETE FROM Availabilities WHERE time = ? AND username = ?";
            PreparedStatement deleteAvailabilityStmt = con.prepareStatement(deleteAvailabilityQuery);
            deleteAvailabilityStmt.setDate(1, d);
            deleteAvailabilityStmt.setString(2, caregiverName);
            deleteAvailabilityStmt.executeUpdate();

            System.out.println("Appointment ID " + appointmentId + ", Caregiver username " + caregiverName);

        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // check 1: login
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }

        // check 2: token length
        if (tokens.length != 2) {
            System.out.println("Please try again");
            return;
        }

        int appointmentId;
        try {
            appointmentId = Integer.parseInt(tokens[1]);
        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace();
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String findAppointmentQuery = "SELECT * FROM Reservations WHERE ID = ?";
        String deleteAppointmentQuery = "DELETE FROM Reservations WHERE ID = ?";
        String addAvailabilityQuery = "INSERT INTO Availabilities (Time, Username) VALUES (?, ?)";
        String updateVaccineQuery = "UPDATE Vaccines SET doses = doses + 1 WHERE name = ?";

        try {
            PreparedStatement findStatement = con.prepareStatement(findAppointmentQuery);
            findStatement.setInt(1, appointmentId);
            ResultSet resultSet = findStatement.executeQuery();

            if (!resultSet.next()) {
                System.out.println("No appointments found");
                return;
            }

            String caregiverName = resultSet.getString("CaregiverName");
            String patientName = resultSet.getString("PatientName");
            String vaccineName = resultSet.getString("VaccineName");
            Date time = resultSet.getDate("Time");

            // user can only cancel their own appointments
            if (currentCaregiver != null && !caregiverName.equals(currentCaregiver.getUsername()) ||
                    currentPatient != null && !patientName.equals(currentPatient.getUsername())) {
                System.out.println("You can only cancel your appointments");
                return;
            }

            // Delete the appointment
            PreparedStatement deleteStatement = con.prepareStatement(deleteAppointmentQuery);
            deleteStatement.setInt(1, appointmentId);
            deleteStatement.executeUpdate();

            // Add the availability back for the caregiver
            PreparedStatement addAvailabilityStatement = con.prepareStatement(addAvailabilityQuery);
            addAvailabilityStatement.setDate(1, time);
            addAvailabilityStatement.setString(2, caregiverName);
            addAvailabilityStatement.executeUpdate();

            // Add dose
            PreparedStatement updateVaccineStatement = con.prepareStatement(updateVaccineQuery);
            updateVaccineStatement.setString(1, vaccineName);
            updateVaccineStatement.executeUpdate();

            System.out.println("Appointment successfully cancelled");
        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace(); // For debugging purposes
        } finally {
            cm.closeConnection();
        }
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        // show_appointments
        // check 1: login
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }

        // check 2: token length
        if (tokens.length != 1) {
            System.out.println("Please try again");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String caregiverQuery = "SELECT ID, VaccineName, Time, PatientName " +
                "FROM Reservations WHERE CaregiverName = ? ORDER BY ID";

        String patientQuery = "SELECT ID, VaccineName, Time, CaregiverName " +
                "FROM Reservations WHERE PatientName = ? ORDER BY ID";

        try {
            PreparedStatement statement;

            if (currentCaregiver != null) {
                statement = con.prepareStatement(caregiverQuery);
                statement.setString(1, currentCaregiver.getUsername());
            } else {
                statement = con.prepareStatement(patientQuery);
                statement.setString(1, currentPatient.getUsername());
            }

            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                int appointmentID = resultSet.getInt("ID");
                String vaccineName = resultSet.getString("VaccineName");
                String date = resultSet.getDate("Time").toString();
                String username = currentCaregiver != null
                        ? resultSet.getString("PatientName")
                        : resultSet.getString("CaregiverName");

                System.out.println(appointmentID + " " + vaccineName + " " + date + " " + username);
            }
        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }

    }

    private static void logout(String[] tokens) {
        // logout
        // check 1: login
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first");
            return;
        }

        // check 2: token length
        if (tokens.length != 1) {
            System.out.println("Please try again");
            return;
        }

        // logout the user
        try {
            currentCaregiver = null;
            currentPatient = null;
            System.out.println("Successfully logged out");
        } catch (Exception e) {
            System.out.println("Please try again");
            e.printStackTrace();
        }
    }

    private static boolean isStrongPassword(String password) {
        // minimum length
        if (password.length() < 8) {
            return false;
        }

        // all requirements
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasNumber = false;
        boolean hasSpecialChar = false;

        String specialCharacters = "!@#?";

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasNumber = true;
            } else if (specialCharacters.contains(String.valueOf(c))) {
                hasSpecialChar = true;
            }
        }

        return hasUppercase && hasLowercase && hasNumber && hasSpecialChar;
    }
}
