package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Classe principale pour orchestrer l'application complète :
 * 1. Exécution de la migration de base de données
 * 2. Initialisation du jeu de données réaliste
 * 3. Démonstration des requêtes JPQL & Criteria API
 * 4. Démonstration du Cache de second niveau L2
 * 5. Génération du rapport de performance Hibernate
 */
public class MainApp {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================");
        System.out.println(" TP CAPSTONE : Application 'Réservation de salles' (JPA/Hibernate)");
        System.out.println("================================================================\n");

        // 1. Détection de l'environnement de base de données
        boolean isMysqlAvailable = checkMysqlPort("localhost", 3306);
        String persistenceUnit = isMysqlAvailable ? "ReservationMySQLPU" : "ReservationPU";

        String jdbcUrl;
        String dbUser;
        String dbPass;

        if (isMysqlAvailable) {
            LOGGER.info("Serveur MySQL détecté sur le port 3306. Utilisation de ReservationMySQLPU.");
            jdbcUrl = "jdbc:mysql://localhost:3306/reservation?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true&serverTimezone=UTC";
            dbUser = "root";
            dbPass = "password";
        } else {
            LOGGER.info("MySQL non détecté sur le port 3306. Utilisation du mode H2 en mémoire (ReservationPU).");
            jdbcUrl = "jdbc:h2:mem:reservation;DB_CLOSE_DELAY=-1;MODE=MySQL";
            dbUser = "sa";
            dbPass = "";
        }

        // 2. Initialisation de l'EntityManagerFactory
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnit);
        EntityManager em = emf.createEntityManager();

        try {
            // 3. Exécution de la migration de schéma via MigrationExecutor
            System.out.println("\n>>> [1/5] Exécution du script de migration JDBC...");
            MigrationExecutor migrationExecutor = new MigrationExecutor(jdbcUrl, dbUser, dbPass);
            migrationExecutor.executeMigration("db/migration/v2_0_add_description.sql");

            // 4. Initialisation du jeu de données réaliste
            System.out.println("\n>>> [2/5] Initialisation du jeu de données réaliste...");
            DataInitializer initializer = new DataInitializer(em);
            initializer.initData();

            // 5. Démonstration des requêtes JPQL et Criteria API
            System.out.println("\n>>> [3/5] Exécution des requêtes d'optimisation (JPQL & Criteria)...");
            ReservationService service = new ReservationService(em);

            LocalDateTime searchDate = LocalDateTime.now();
            List<Reservation> jpqlResults = service.findReservationsBySalleAndDate("Salle A", searchDate);
            System.out.println("-> Résultat JPQL (Salle A après maintenant) : " + jpqlResults.size()
                    + " réservation(s) trouvée(s).");
            for (Reservation r : jpqlResults) {
                System.out.println("   * [" + r.getId() + "] " + r.getMotif() + " (" + r.getDateDebut() + " -> "
                        + r.getDateFin() + ")");
            }

            List<Reservation> criteriaResults = service.findReservationsCriteria("Salle A", searchDate);
            System.out.println("-> Résultat Criteria API (Salle A après maintenant) : " + criteriaResults.size()
                    + " réservation(s) trouvée(s).");

            // 6. Démonstration du Cache de second niveau
            System.out.println("\n>>> [4/5] Démonstration du Cache de second niveau (L2 Cache)...");
            em.clear(); // Vider le cache de premier niveau (EntityManager)

            // Récupération dynamique d'une salle pour la démonstration
            Salle sampleSalle = em.createQuery("SELECT s FROM Salle s ORDER BY s.id ASC", Salle.class)
                    .setMaxResults(1)
                    .getSingleResult();
            Long sampleSalleId = sampleSalle.getId();

            // Premier chargement : met l'entité dans le cache L2
            Salle salleFirstLoad = em.find(Salle.class, sampleSalleId);
            System.out.println(
                    "-> Premier accès (Session 1) : " + salleFirstLoad.getNom() + " [id=" + sampleSalleId + "]");

            // Ouvrir une NOUVELLE session EntityManager pour prouver l'effet du cache L2
            // partagé
            EntityManager em2 = emf.createEntityManager();
            try {
                Salle salleSecondLoad = em2.find(Salle.class, sampleSalleId);
                System.out.println("-> Second accès (Session 2 distincte, depuis L2 Cache) : "
                        + salleSecondLoad.getNom() + " [id=" + sampleSalleId + "]");
            } finally {
                em2.close();
            }

            // 7. Génération du rapport de performance
            System.out.println("\n>>> [5/5] Génération du rapport de performance...");
            PerformanceReport report = new PerformanceReport(em);
            report.printReport();

        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
            if (emf != null && emf.isOpen()) {
                emf.close();
            }
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            } catch (Throwable ignored) {
            }
        }

        System.out.println("\n Application exécutée avec succès !");
    }

    private static boolean checkMysqlPort(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
