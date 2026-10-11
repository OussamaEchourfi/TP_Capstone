package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service métier pour la gestion des réservations.
 * Gère les transactions RESOURCE_LOCAL, le verrouillage optimiste (@Version),
 * les requêtes JPQL et Criteria API, ainsi que la journalisation.
 */
public class ReservationService {

    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());
    private final EntityManager em;

    public ReservationService(EntityManager em) {
        this.em = em;
    }

    /**
     * Crée une nouvelle réservation avec vérification de cohérence des dates
     * et gestion de transaction RESOURCE_LOCAL.
     */
    public Reservation createReservation(Reservation reservation) {
        if (reservation.getDateDebut() == null || reservation.getDateFin() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }
        if (!reservation.getDateDebut().isBefore(reservation.getDateFin())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin.");
        }

        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // Vérification de la disponibilité de la salle
            if (reservation.getSalle() != null && reservation.getSalle().getId() != null) {
                if (!isSalleDisponible(reservation.getSalle().getId(), reservation.getDateDebut(), reservation.getDateFin(), null)) {
                    throw new IllegalStateException("La salle est déjà réservée sur ce créneau horaire.");
                }
            }

            em.persist(reservation);
            tx.commit();

            String userEmail = reservation.getUtilisateur() != null ? reservation.getUtilisateur().getEmail() : "inconnu";
            LOGGER.info("Création de la réservation réussie pour utilisateur : " + userEmail);
            return reservation;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Erreur lors de la création de la réservation : " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Recherche les réservations via JPQL paramétré.
     */
    public List<Reservation> findReservationsBySalleAndDate(String nomSalle, LocalDateTime date) {
        LOGGER.info("Recherche JPQL des réservations pour la salle '" + nomSalle + "' après " + date);
        String jpql = "SELECT r FROM Reservation r WHERE r.salle.nom = :nomSalle AND r.dateDebut > :date";
        return em.createQuery(jpql, Reservation.class)
                .setParameter("nomSalle", nomSalle)
                .setParameter("date", date)
                .getResultList();
    }

    /**
     * Recherche les réservations via Criteria API.
     */
    public List<Reservation> findReservationsCriteria(String nomSalle, LocalDateTime date) {
        LOGGER.info("Recherche Criteria API des réservations pour la salle '" + nomSalle + "' après " + date);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
        Root<Reservation> root = cq.from(Reservation.class);

        Predicate predicate = cb.and(
                cb.equal(root.get("salle").get("nom"), nomSalle),
                cb.greaterThan(root.get("dateDebut"), date)
        );

        cq.select(root).where(predicate);
        return em.createQuery(cq).getResultList();
    }

    /**
     * Vérifie si une salle est disponible sur un intervalle de temps donné.
     */
    public boolean isSalleDisponible(Long salleId, LocalDateTime debut, LocalDateTime fin, Long excludeReservationId) {
        String jpql = "SELECT COUNT(r) FROM Reservation r " +
                "WHERE r.salle.id = :salleId " +
                "AND r.dateDebut < :fin " +
                "AND r.dateFin > :debut";

        if (excludeReservationId != null) {
            jpql += " AND r.id != :excludeId";
        }

        var query = em.createQuery(jpql, Long.class)
                .setParameter("salleId", salleId)
                .setParameter("debut", debut)
                .setParameter("fin", fin);

        if (excludeReservationId != null) {
            query.setParameter("excludeId", excludeReservationId);
        }

        Long count = query.getSingleResult();
        return count == 0;
    }

    /**
     * Met à jour une réservation avec gestion du verrouillage optimiste (@Version).
     */
    public Reservation updateReservation(Reservation reservation) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Reservation updated = em.merge(reservation);
            tx.commit();
            LOGGER.info("Réservation mise à jour avec succès : id=" + updated.getId());
            return updated;
        } catch (OptimisticLockException ole) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.warning("Conflit de verrouillage optimiste détecté sur la réservation : " + ole.getMessage());
            System.out.println("Conflit détecté, veuillez réessayer.");
            throw ole;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour de la réservation : " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Supprime une réservation par son identifiant.
     */
    public void deleteReservation(Long id) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Reservation r = em.find(Reservation.class, id);
            if (r != null) {
                em.remove(r);
                LOGGER.info("Réservation supprimée : id=" + id);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression de la réservation : " + e.getMessage(), e);
            throw e;
        }
    }
}
