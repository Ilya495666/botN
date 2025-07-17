package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class BotOk extends TelegramLongPollingBot {
    private static final String DB_URL = "jdbc:sqlite:kids_club.db";

    // Состояния пользователей
    private enum BookingState {
        START,
        SELECT_COURSE,
        SELECT_AGE,
        SELECT_DAY,
        SELECT_TIME,
        CONFIRM
    }

    private static class UserState {
        BookingState state;
        String selectedCourse;
        String selectedAge;
        String selectedDay;
        String selectedTime;
        int scheduleId;
    }

    private final Map<Long, UserState> userStates = new HashMap<>();

    @Override
    public String getBotUsername() {
        return "clubOstrovOk_bot";
    }

    @Override
    public String getBotToken() {
        return "7828373193:AAGPrdyabuxT9mFtBIcCZxVsuLECzoIgsEs";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            try {
                if (messageText.equals("/start")) {
                    sendStartMessage(chatId);
                } else if (messageText.equals("📅 Расписание")) {
                    sendSchedule(chatId);
                } else if (messageText.equals("📝 Записаться") || messageText.equals("↩️ Назад")) {
                    startBookingProcess(chatId);
                } else if (userStates.containsKey(chatId)) {
                    processBookingStep(chatId, messageText);
                } else {
                    sendMessage(chatId, "Используйте кнопки меню");
                }
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ Ошибка. Попробуйте позже.");
                e.printStackTrace();
            }
        }
    }

    private void startBookingProcess(long chatId) {
        userStates.put(chatId, new UserState());
        userStates.get(chatId).state = BookingState.SELECT_COURSE;
        sendCourseSelection(chatId);
    }

    private void processBookingStep(long chatId, String messageText) throws SQLException {
        UserState userState = userStates.get(chatId);
        if (userState == null) {
            sendStartMessage(chatId);
            return;
        }

        switch (userState.state) {
            case SELECT_COURSE:
                handleCourseSelection(chatId, messageText);
                break;
            case SELECT_AGE:
                handleAgeSelection(chatId, messageText);
                break;
            case SELECT_DAY:
                handleDaySelection(chatId, messageText);
                break;
            case SELECT_TIME:
                handleTimeSelection(chatId, messageText);
                break;
            case CONFIRM:
                handleConfirmation(chatId, messageText);
                break;
            default:
                sendMessage(chatId, "Неизвестная команда");
        }
    }

    private void sendCourseSelection(long chatId) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT activity FROM schedule")) {

            List<String> courses = new ArrayList<>();
            while (rs.next()) {
                courses.add(rs.getString("activity"));
            }

            if (courses.isEmpty()) {
                sendMessage(chatId, "Нет доступных курсов");
                return;
            }

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Выберите курс:");
            message.setReplyMarkup(createKeyboard(courses, true));

            execute(message);
        } catch (SQLException | TelegramApiException e) {
            sendMessage(chatId, "Ошибка при загрузке курсов");
            e.printStackTrace();
        }
    }

    private void handleCourseSelection(long chatId, String course) {
        UserState userState = userStates.get(chatId);
        userState.selectedCourse = course;
        userState.state = BookingState.SELECT_AGE;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT DISTINCT age_group FROM schedule WHERE activity = ?")) {

            pstmt.setString(1, course);
            ResultSet rs = pstmt.executeQuery();

            List<String> ageGroups = new ArrayList<>();
            while (rs.next()) {
                ageGroups.add(rs.getString("age_group"));
            }

            if (ageGroups.isEmpty()) {
                sendMessage(chatId, "Нет доступных возрастных групп для этого курса");
                startBookingProcess(chatId);
                return;
            }

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Выберите возрастную группу для курса '" + course + "':");
            message.setReplyMarkup(createKeyboard(ageGroups, true));

            execute(message);
        } catch (SQLException | TelegramApiException e) {
            sendMessage(chatId, "Ошибка при загрузке возрастных групп");
            e.printStackTrace();
        }
    }

    private void handleAgeSelection(long chatId, String ageGroup) {
        UserState userState = userStates.get(chatId);
        userState.selectedAge = ageGroup;
        userState.state = BookingState.SELECT_DAY;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT DISTINCT day FROM schedule WHERE activity = ? AND age_group = ?")) {

            pstmt.setString(1, userState.selectedCourse);
            pstmt.setString(2, ageGroup);
            ResultSet rs = pstmt.executeQuery();

            List<String> days = new ArrayList<>();
            while (rs.next()) {
                days.add(rs.getString("day"));
            }

            if (days.isEmpty()) {
                sendMessage(chatId, "❌ Для выбранного курса и возраста нет доступных дней");
                startBookingProcess(chatId);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Выберите день для курса '" + userState.selectedCourse +
                        "' (" + ageGroup + "):");
                message.setReplyMarkup(createKeyboard(days, true));
                execute(message);
            }
        } catch (SQLException | TelegramApiException e) {
            sendMessage(chatId, "Ошибка при загрузке дней");
            e.printStackTrace();
        }
    }

    private void handleDaySelection(long chatId, String day) {
        UserState userState = userStates.get(chatId);
        userState.selectedDay = day;
        userState.state = BookingState.SELECT_TIME;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT id, time FROM schedule WHERE activity = ? AND age_group = ? AND day = ? " +
                             "AND booked_places < max_places")) {

            pstmt.setString(1, userState.selectedCourse);
            pstmt.setString(2, userState.selectedAge);
            pstmt.setString(3, day);
            ResultSet rs = pstmt.executeQuery();

            List<String> times = new ArrayList<>();
            while (rs.next()) {
                times.add(rs.getString("time"));
            }

            if (times.isEmpty()) {
                sendMessage(chatId, "❌ На выбранный день нет свободных мест");
                userState.state = BookingState.SELECT_DAY;
                handleAgeSelection(chatId, userState.selectedAge);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Выберите время для курса '" + userState.selectedCourse +
                        "' (" + userState.selectedAge + ") в " + day + ":");
                message.setReplyMarkup(createKeyboard(times, true));
                execute(message);
            }
        } catch (SQLException | TelegramApiException e) {
            sendMessage(chatId, "Ошибка при загрузке времени");
            e.printStackTrace();
        }
    }

    private void handleTimeSelection(long chatId, String time) {
        UserState userState = userStates.get(chatId);
        userState.selectedTime = time;
        userState.state = BookingState.CONFIRM;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT id FROM schedule WHERE activity = ? AND age_group = ? AND day = ? AND time = ?")) {

            pstmt.setString(1, userState.selectedCourse);
            pstmt.setString(2, userState.selectedAge);
            pstmt.setString(3, userState.selectedDay);
            pstmt.setString(4, time);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                userState.scheduleId = rs.getInt("id");

                List<String> options = Arrays.asList("✅ Подтвердить", "↩️ Назад");
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Подтвердите запись:\n\n" +
                        "Курс: " + userState.selectedCourse + "\n" +
                        "Возраст: " + userState.selectedAge + "\n" +
                        "День: " + userState.selectedDay + "\n" +
                        "Время: " + time);
                message.setReplyMarkup(createKeyboard(options, false));
                execute(message);
            }
        } catch (SQLException | TelegramApiException e) {
            sendMessage(chatId, "Ошибка при подтверждении записи");
            e.printStackTrace();
        }
    }

    private void handleConfirmation(long chatId, String confirmation) {
        UserState userState = userStates.get(chatId);

        if (confirmation.equals("✅ Подтвердить")) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE schedule SET booked_places = booked_places + 1 WHERE id = ? AND booked_places < max_places")) {

                pstmt.setInt(1, userState.scheduleId);
                int updated = pstmt.executeUpdate();

                if (updated > 0) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO bookings (user_id, child_name, schedule_id) VALUES (?, ?, ?)")) {

                        insertStmt.setLong(1, chatId);
                        insertStmt.setString(2, "Ребенок");
                        insertStmt.setInt(3, userState.scheduleId);
                        insertStmt.executeUpdate();
                    }

                    sendMessage(chatId, "✅ Запись успешно оформлена!");
                    sendStartMessage(chatId);
                } else {
                    sendMessage(chatId, "❌ Места закончились. Пожалуйста, выберите другое время.");
                    userState.state = BookingState.SELECT_DAY;
                    handleAgeSelection(chatId, userState.selectedAge);
                }
            } catch (SQLException e) {
                sendMessage(chatId, "Ошибка при оформлении записи");
                e.printStackTrace();
            }
        } else {
            userState.state = BookingState.SELECT_DAY;
            handleAgeSelection(chatId, userState.selectedAge);
        }

        userStates.remove(chatId);
    }

    private ReplyKeyboardMarkup createKeyboard(List<String> options, boolean withBack) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        if (options != null && !options.isEmpty()) {
            // Разбиваем варианты на ряды по 2 кнопки
            for (int i = 0; i < options.size(); i += 2) {
                KeyboardRow row = new KeyboardRow();
                row.add(new KeyboardButton(options.get(i)));
                if (i + 1 < options.size()) {
                    row.add(new KeyboardButton(options.get(i + 1)));
                }
                rows.add(row);
            }
        }

        // Добавляем кнопку "Назад" если нужно
        if (withBack) {
            KeyboardRow backRow = new KeyboardRow();
            backRow.add(new KeyboardButton("↩️ Назад"));
            rows.add(backRow);
        }

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        return keyboard;
    }

    private void sendSchedule(long chatId) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                SELECT day, time, activity, age_group, 
                       (max_places - booked_places) as free_places 
                FROM schedule 
                ORDER BY 
                    CASE day
                        WHEN 'Понедельник' THEN 1
                        WHEN 'Вторник' THEN 2
                        WHEN 'Среда' THEN 3
                        WHEN 'Четверг' THEN 4
                        WHEN 'Пятница' THEN 5
                        WHEN 'Суббота' THEN 6
                        ELSE 7
                    END, time""")) {

            StringBuilder schedule = new StringBuilder("📅 Расписание занятий:\n\n");
            String currentDay = "";

            while (rs.next()) {
                String day = rs.getString("day");
                if (!day.equals(currentDay)) {
                    schedule.append("\n📌 ").append(day.toUpperCase()).append("\n");
                    currentDay = day;
                }
                schedule.append(String.format(
                        "🕓 %s - %s\n   👶 %s\n   🆓 Свободных мест: %d\n\n",
                        rs.getString("time"),
                        rs.getString("activity"),
                        rs.getString("age_group"),
                        rs.getInt("free_places")
                ));
            }

            sendMessage(chatId, schedule.toString());

        } catch (SQLException e) {
            sendMessage(chatId, "⚠️ Ошибка при загрузке расписания");
            e.printStackTrace();
        }
    }

    private void sendStartMessage(long chatId) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📅 Расписание"));
        row1.add(new KeyboardButton("📝 Записаться"));

        rows.add(row1);
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("👋 Добро пожаловать в детский клуб 'ОстровОК'!\n\nВыберите действие:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}