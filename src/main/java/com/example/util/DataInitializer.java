package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initialiseur de jeu de données réaliste pour tester l'application.
 */
public class DataInitializer {

    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());
    private final EntityManager em;

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        Long userCount = em.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class).getSingleResult();
        if (userCount != null && userCount > 0) {
            LOGGER.info("Jeu de données déjà présent en base (" + userCount + " utilisateur(s) existant(s)). Initialisation ignorée.");
            return;
        }

        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // Création des utilisateurs
            Utilisateur user1 = new Utilisateur("Alice Dupont", "alice.dupont@example.com");
            Utilisateur user2 = new Utilisateur("Bob Martin", "bob.martin@example.com");
            Utilisateur user3 = new Utilisateur("Charlie Durand", "charlie.durand@example.com");
            Utilisateur user4 = new Utilisateur("Diana Prince", "diana.prince@example.com");

            em.persist(user1);
            em.persist(user2);
            em.persist(user3);
            em.persist(user4);

            // Création des salles
            Salle salle1 = new Salle("Salle A", 20, "Salle de réunion équipée d'un vidéoprojecteur");
            Salle salle2 = new Salle("Salle B", 50, "Grande salle de conférence avec sonorisation");
            Salle salle3 = new Salle("Salle C", 10, "Espace de travail agile et brainstorming");
            Salle salle4 = new Salle("Salle D", 100, "Amphithéâtre pour séminaires et présentations");

            em.persist(salle1);
            em.persist(salle2);
            em.persist(salle3);
            em.persist(salle4);

            // Création des réservations réalistes (dates dynamiques)
            LocalDateTime now = LocalDateTime.now();

            // Réservation 1 : Demain matin pour Alice dans la Salle A
            Reservation res1 = new Reservation(
                    user1,
                    salle1,
                    now.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                    now.plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0),
                    "Revue de sprint agile"
            );
            em.persist(res1);

            // Réservation 2 : Après-demain pour Bob dans la Salle B
            Reservation res2 = new Reservation(
                    user2,
                    salle2,
                    now.plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0),
                    now.plusDays(2).withHour(17).withMinute(0).withSecond(0).withNano(0),
                    "Présentation des résultats semestriels"
            );
            em.persist(res2);

            // Réservation 3 : Dans 3 jours pour Charlie dans la Salle C
            Reservation res3 = new Reservation(
                    user3,
                    salle3,
                    now.plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0),
                    now.plusDays(3).withHour(12).withMinute(0).withSecond(0).withNano(0),
                    "Session de brainstorming architecture"
            );
            em.persist(res3);

            // Réservation 4 : Dans 5 jours pour Diana dans la Salle A
            Reservation res4 = new Reservation(
                    user4,
                    salle1,
                    now.plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0),
                    now.plusDays(5).withHour(16).withMinute(0).withSecond(0).withNano(0),
                    "Formation sécurité applicative"
            );
            em.persist(res4);

            tx.commit();
            LOGGER.info("Jeu de données réaliste initialisé avec succès (4 utilisateurs, 4 salles, 4 réservations).");
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des données : " + e.getMessage(), e);
            throw e;
        }
    }
}
