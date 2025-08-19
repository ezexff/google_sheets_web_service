package com.google_sheets_service;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.jws.WebService;
import javax.jws.WebParam;
import javax.naming.InitialContext;
import javax.sql.DataSource;

@WebService(serviceName = "google_sheets_service")
public class google_sheets_service {

    /**
     * @t Function
     * @description Логирование
     */
    private void logError(String functionName, String device_id, String description, String errorText) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());

        System.out.println("LogError: " + formatter.format(date) + " |  Function: " + functionName + " | called by [" + device_id + "] | Description or Result: " + description + " | Error text: " + errorText);
    }

    private void log(String functionName, String device_id, String description) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());

        //System.out.println("Log: " + formatter.format(date) + " |  Function: " + functionName + " | called by [" + device_id + "] | Description or Result: " + description);
        System.out.println("Log: " + formatter.format(date) + " | Called by [" + device_id + "] | Function: " + functionName + " | Description or Result: " + description);
    }

    /**
     * @t Function
     * @description Авторизация в системе с Google листами
     */
    public String tryLogin(
            @WebParam(name = "login") String login,
            @WebParam(name = "password") String password,
            @WebParam(name = "device_id") String device_id) {

        String CURRENT_FUNCTION = new Throwable().getStackTrace()[0].getMethodName();

        log(CURRENT_FUNCTION, device_id, "Вызван метод");

        String JsonOutput = ""; // Результат успешного получения данных в виде JSON строки
        GetUserInfoFromDatabaseResponse userInfo = new GetUserInfoFromDatabaseResponse(); // Пользовательские данные с результатом извлечения

        DataSource ds;
        try {
            // Инициализация контекста и источника данных
            InitialContext initContext = new InitialContext();
            ds = (DataSource) initContext.lookup("java:comp/env/jdbc/oracle11g");

            // Подключение к БД, проверка пароля и получение данных пользователя
            try (Connection con = ds.getConnection();
                    Statement stmt = con.createStatement();) {
                String password_from_user = password;
                String password_from_database = "";
                String user_id = "";

                String last_name = "";
                String first_name = "";
                String second_name = "";

                // Получение password, last_name, first_name, second_name, user_id по login из таблицы USER_TABLE
                try (ResultSet rs = stmt.executeQuery("select PASSWORD, LAST_NAME, FIRST_NAME, SECOND_NAME, id from Z_USERS where LOGIN='" + login + "'")) {

                    if (!rs.isBeforeFirst()) {
                        log(CURRENT_FUNCTION, device_id, "Неверный логин или пароль");
                        userInfo.success = false;
                        userInfo.errorMessage = "Неверный логин или пароль";
                        Gson gson = new Gson();
                        return gson.toJson(userInfo);
                    }

                    while (rs.next()) {
                        password_from_database = rs.getString(1);
                        last_name = rs.getString(2);
                        first_name = rs.getString(3);
                        second_name = rs.getString(4);
                        user_id = rs.getString(5);
                    }
                }

                // Сравнения хеша password из бд с хешем введённого password
                if (BCrypt.checkpw(password_from_user, password_from_database)) {

                    userInfo.id = user_id;
                    userInfo.last_name = last_name;
                    userInfo.first_name = first_name;
                    userInfo.second_name = second_name;
                    userInfo.success = true;

                    Gson gson = new Gson();
                    JsonOutput = gson.toJson(userInfo);

                } else {
                    log(CURRENT_FUNCTION, device_id, "Неверный логин или пароль");

                    userInfo.success = false;
                    userInfo.errorMessage = "Неверный логин или пароль";
                    Gson gson = new Gson();
                    return gson.toJson(userInfo);
                }
            } catch (SQLException e) {
                logError(CURRENT_FUNCTION, device_id, "Не удалось установить соединение с БД", e.toString());

                userInfo.success = false;
                userInfo.errorMessage = "Не удалось установить соединение с БД";
                Gson gson = new Gson();
                return gson.toJson(userInfo);
            } catch (Exception e) {
                logError(CURRENT_FUNCTION, device_id, "Ошибка", e.toString());

                userInfo.success = false;
                userInfo.errorMessage = "Неизвестная ошибка";
                Gson gson = new Gson();
                return gson.toJson(userInfo);
            }
        } catch (Exception e) {
            logError(CURRENT_FUNCTION, device_id, "Не удалось открыть источник данных из Context.xml", e.toString());

            userInfo.success = false;
            userInfo.errorMessage = "Не удалось открыть источник данных из Context.xml";
            Gson gson = new Gson();
            return gson.toJson(userInfo);
        }

        log(CURRENT_FUNCTION, device_id, "Результат работы функции: " + JsonOutput);
        return JsonOutput;
    }

    /**
     * @t Function
     * @description Получение листов
     */
    public String getGSheets(
            @WebParam(name = "user_id") String user_id,
            @WebParam(name = "device_id") String device_id) {

        String CURRENT_FUNCTION = new Throwable().getStackTrace()[0].getMethodName();

        log(CURRENT_FUNCTION, device_id, "Вызван метод");

        String JsonOutput = ""; // Результат успешного получения данных в виде JSON строки
        GetGSheetsFromDatabaseResponse sheets = new GetGSheetsFromDatabaseResponse(); // Листы с результатом извлечения

        DataSource ds;
        try {
            // Инициализация контекста и источника данных 
            InitialContext initContext = new InitialContext();
            ds = (DataSource) initContext.lookup("java:comp/env/jdbc/oracle11g");

            // Подключение к БД, загрузка листов
            try (Connection con = ds.getConnection();
                    Statement stmt = con.createStatement();) {

                List sheets_id = new ArrayList();

                // Получение всех sheet_id по user_id
                try (ResultSet rs = stmt.executeQuery("select sheet_id from z_users_and_sheets where user_id='"
                        + user_id + "'")) {

                    if (!rs.isBeforeFirst()) {
                        log(CURRENT_FUNCTION, device_id, "Не удалось найти листы в базе данных");
                        sheets.success = false;
                        sheets.errorMessage = "Не удалось информацию о листах в БД";
                        Gson gson = new Gson();
                        return gson.toJson(sheets);
                    }

                    while (rs.next()) {
                        sheets_id.add(rs.getString(1));
                    }
                }

                List<GSheet> list_sheets = new ArrayList();

                // Получение всех sheets по sheet_id из таблицы z_gsheets
                for (int i = 0; i < sheets_id.size(); i++) {
                    try (ResultSet rs = stmt.executeQuery("select title, description, google_api_key, spreadsheet_id, name, type from z_gsheets where id='" + sheets_id.get(i) + "'")) {

                        while (rs.next()) {

                            GSheet sheet = new GSheet();
                            sheet.title = rs.getString(1);
                            sheet.description = rs.getString(2);
                            sheet.google_api_key = rs.getString(3);
                            sheet.spreadsheet_id = rs.getString(4);
                            sheet.name = rs.getString(5);
                            sheet.type = rs.getString(6);

                            list_sheets.add(sheet);
                        }
                    }
                }

                // По sheet_id не было найдено записей в таблице z_gsheets
                if (list_sheets.isEmpty()) {
                    log(CURRENT_FUNCTION, device_id, "Не удалось найти данные листов в БД");

                    sheets.success = false;
                    sheets.errorMessage = "Не удалось найти данные листов в БД";
                    Gson gson = new Gson();
                    return gson.toJson(sheets);
                }

                sheets.sheets = list_sheets;
                sheets.success = true;

                Gson gson = new Gson();
                JsonOutput = gson.toJson(sheets);

            } catch (SQLException e) {
                logError(CURRENT_FUNCTION, device_id, "Не удалось установить соединение с БД", e.toString());

                sheets.success = false;
                sheets.errorMessage = "Не удалось установить соединение с БД";
                Gson gson = new Gson();
                return gson.toJson(sheets);
            } catch (Exception e) {
                logError(CURRENT_FUNCTION, device_id, "Ошибка", e.toString());

                sheets.success = false;
                sheets.errorMessage = "Неизвестная ошибка";
                Gson gson = new Gson();
                return gson.toJson(sheets);
            }
        } catch (Exception e) {
            logError(CURRENT_FUNCTION, device_id, "Не удалось открыть источник данных из Context.xml", e.toString());

            sheets.success = false;
            sheets.errorMessage = "Не удалось открыть источник данных из Context.xml";
            Gson gson = new Gson();
            return gson.toJson(sheets);
        }

        log(CURRENT_FUNCTION, device_id, "Результат работы функции: " + JsonOutput);
        return JsonOutput;
    }
}