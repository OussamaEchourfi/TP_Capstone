package com.example.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exécuteur de scripts de migration de base de données via JDBC.
 */
public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeMigration(String scriptPath) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(scriptPath);
        if (is == null) {
            throw new IllegalArgumentException("Script de migration introuvable dans le classpath : " + scriptPath);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder scriptContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Ignorer les lignes de commentaires SQL
                if (!trimmed.startsWith("--")) {
                    scriptContent.append(line).append("\n");
                }
            }

            // Découpage des requêtes par le point-virgule pour une compatibilité JDBC totale
            String[] statements = scriptContent.toString().split(";");

            try (Statement stmt = conn.createStatement()) {
                for (String rawSql : statements) {
                    String sql = rawSql.trim();
                    if (!sql.isEmpty()) {
                        LOGGER.info("Exécution migration SQL : " + sql);
                        try {
                            stmt.execute(sql);
                        } catch (SQLException e) {
                            // Tolérer si la colonne ou l'index existe déjà pour rendre le script idempotent
                            if (e.getMessage() != null &&
                                    (e.getMessage().toLowerCase().contains("duplicate") ||
                                     e.getMessage().toLowerCase().contains("already exists"))) {
                                LOGGER.warning("Notice migration (déjà appliqué) : " + e.getMessage());
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
            LOGGER.info("Migration exécutée avec succès depuis " + scriptPath);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Échec lors de l'exécution de la migration : " + e.getMessage(), e);
            throw e;
        }
    }
}
