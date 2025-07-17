package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.sql.*;
import org.example.bot.BotOk;

public class Main {
    static {
        // Разрешаем загрузку нативных библиотек SQLite
        System.setProperty("org.sqlite.lib.allowLoad", "true");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Ошибка загрузки драйвера SQLite:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // Инициализация базы данных
            initializeDatabase();

            // Запуск бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new BotOk());
            System.out.println("Бот успешно запущен!");

            // Вывод текущего расписания в консоль для проверки
            printScheduleTable();

        } catch (Exception e) {
            System.err.println("Ошибка при запуске бота:");
            e.printStackTrace();
        }
    }

    private static void initializeDatabase() {
        String url = "jdbc:sqlite:kids_club.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // 1. Создаем основную таблицу занятий
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS schedule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                activity TEXT NOT NULL,
                day TEXT NOT NULL,
                time TEXT NOT NULL,
                max_places INTEGER NOT NULL,
                booked_places INTEGER DEFAULT 0,
                age_group TEXT NOT NULL,
                min_age INTEGER,
                UNIQUE(activity, day, time)
            )""");

            // 2. Создаем таблицу записей
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                child_name TEXT,
                schedule_id INTEGER NOT NULL,
                booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (schedule_id) REFERENCES schedule(id)
            )""");

            // 3. Проверяем, есть ли уже данные в таблице
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schedule");
            int count = rs.getInt(1);

            if (count == 0) {
                // 4. Добавляем полный набор тестовых данных
                String[] days = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"};

                // Подготовленный запрос для вставки
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO schedule (activity, day, time, max_places, age_group, min_age) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {

                    // Данные для вставки
                    Object[][] scheduleData = {
                            // Понедельник
                            {"Догоридмика+", days[0], "16.00-18.00", 10, "6+ лет", 6},
                            {"Чтение", days[0], "18.20-19.00", 8, "6+ лет", 6},
                            {"Букварёнок (1 год)", days[0], "19.10-19.50", 6, "6 лет", 6},

                            // Вторник
                            {"Я сам!", days[1], "09.00-12.00", 5, "3-5 лет", 3},
                            {"Академия игры", days[1], "10.00-10.40", 8, "4-6 лет", 4},
                            {"Букварёнок (1 год)", days[1], "17.30-18.10", 6, "6 лет", 6},

                            // Среда
                            {"Букварёнок (2 год)", days[2], "16.00-17.00", 6, "6 лет", 6},
                            {"Английский язык", days[2], "15.30-16.10", 8, "7 класс", 12},
                            {"Пре-Букварёнок", days[2], "17.00-18.00", 6, "5 лет", 5},
                            {"Говорим красиво и правильно!", days[2], "16.20-17.00", 8, "4-6 лет", 4},
                            {"Букварёнок", days[2], "18.10-19.10", 6, "4 года", 4},
                            {"Английский язык", days[2], "17.20-18.00", 8, "4-5 лет", 4},
                            {"Цейродианализа", days[2], "18.20-19.00", 5, "2-4 года", 2},
                            {"Цейродианализа", days[2], "19.10-19.50", 5, "5-8 лет", 5},

                            // Четверг
                            {"Английский язык", days[3], "16.20-17.20", 8, "4-5 лет", 4},
                            {"Говорим красиво и правильно!", days[3], "17.30-18.10", 8, "4-6 лет", 4},

                            // Пятница
                            {"Я сам!", days[4], "09.00-12.00", 5, "3-5 лет", 3},
                            {"Академия игры", days[4], "10.00-10.40", 8, "4-6 лет", 4},
                            {"Догоридмика+", days[4], "16.00-18.00", 10, "6+ лет", 6},
                            {"Чтение", days[4], "18.20-19.00", 8, "6+ лет", 6},

                            // Суббота
                            {"Английский язык", days[5], "10.30-11.10", 8, "4-5 лет", 4},
                            {"Цейродианализа", days[5], "11.20-12.00", 5, "2-4 года", 2},
                            {"Букварёнок", days[5], "12.10-12.50", 6, "4 года", 4}
                    };

                    // Вставляем все данные
                    for (Object[] data : scheduleData) {
                        pstmt.setString(1, (String) data[0]);
                        pstmt.setString(2, (String) data[1]);
                        pstmt.setString(3, (String) data[2]);
                        pstmt.setInt(4, (Integer) data[3]);
                        pstmt.setString(5, (String) data[4]);
                        pstmt.setInt(6, (Integer) data[5]);
                        pstmt.executeUpdate();
                    }
                }

                System.out.println("Добавлено " + scheduleData.length + " занятий в расписание");
            } else {
                System.out.println("В базе уже есть " + count + " занятий, новые не добавлены");
            }

            // 5. Создаем индекс для ускорения поиска
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_schedule_day ON schedule(day)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_schedule_activity ON schedule(activity)");

        } catch (SQLException e) {
            System.err.println("Ошибка инициализации базы данных:");
            e.printStackTrace();
            throw new RuntimeException("Не удалось инициализировать базу данных", e);
        }
    }

    public static void printScheduleTable() {
        System.out.println("\nТекущее расписание в базе:");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:kids_club.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM schedule")) {

            while (rs.next()) {
                System.out.printf("%d | %-12s | %-10s | %5s | %d/%d%n",
                        rs.getInt("id"),
                        rs.getString("activity"),
                        rs.getString("day"),
                        rs.getString("time"),
                        rs.getInt("booked_places"),
                        rs.getInt("max_places"));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при чтении расписания:");
            e.printStackTrace();
        }
    }
}