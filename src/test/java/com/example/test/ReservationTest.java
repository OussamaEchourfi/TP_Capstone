package com.example.test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;

public class ReservationTest {

    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    public static void init() {
        emf = Persistence.createEntityManagerFactory("ReservationPU");
    }

    @BeforeEach
    public void setUp() {
        em = emf.createEntityManager();
    }

    @AfterEach
    public void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    @AfterAll
    public static void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    public void testCreateReservation() {
        em.getTransaction().begin();

        Utilisateur user = new Utilisateur();
        user.setNom("Test User");
        user.setEmail("test.user" + System.currentTimeMillis() + "@example.com");
        em.persist(user);

        Salle salle = new Salle();
        salle.setNom("Salle Test Create");
        salle.setCapacite(25);
        em.persist(salle);

        Reservation reservation = new Reservation();
        reservation.setUtilisateur(user);
        reservation.setSalle(salle);
        reservation.setDateDebut(LocalDateTime.now().plusDays(2));
        reservation.setDateFin(LocalDateTime.now().plusDays(2).plusHours(1));
        reservation.setMotif("Test de réservation unitaire");

        em.persist(reservation);
        em.getTransaction().commit();

        Assertions.assertNotNull(reservation.getId(), "L'identifiant de la réservation généré ne doit pas être nul");
        Assertions.assertTrue(reservation.getId() > 0);
    }

    @Test
    public void testSecondLevelCache() {
        // 1. Créer et persister une Salle dans une première transaction
        em.getTransaction().begin();
        Salle salle = new Salle("Salle Cache Test", 30, "Salle pour tester le cache L2");
        em.persist(salle);
        em.getTransaction().commit();
        Long salleId = salle.getId();

        Session session = em.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        stats.clear();

        // 2. Ouvrir une session 1 et charger la salle
        EntityManager em1 = emf.createEntityManager();
        Salle loaded1 = em1.find(Salle.class, salleId);
        Assertions.assertNotNull(loaded1);
        em1.close();

        long hitsBefore = stats.getSecondLevelCacheHitCount();

        // 3. Ouvrir une session 2 totalement distincte et recharger la salle
        // Elle doit être résolue depuis le Cache de second niveau (Ehcache) sans requête SQL
        EntityManager em2 = emf.createEntityManager();
        Salle loaded2 = em2.find(Salle.class, salleId);
        Assertions.assertNotNull(loaded2);
        em2.close();

        long hitsAfter = stats.getSecondLevelCacheHitCount();
        System.out.println("L2 Cache Hits avant: " + hitsBefore + ", après: " + hitsAfter);
        Assertions.assertTrue(hitsAfter > hitsBefore, "Le cache L2 aurait dû être sollicité pour l'entité Salle");
    }

    @Test
    public void testOptimisticLocking() {
        // 1. Créer une salle initiale
        em.getTransaction().begin();
        Salle salle = new Salle("Salle Lock Test", 15);
        em.persist(salle);
        em.getTransaction().commit();
        Long salleId = salle.getId();

        // 2. Charger l'entité dans deux EntityManagers différents
        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();

        Salle salleEm1 = em1.find(Salle.class, salleId);
        Salle salleEm2 = em2.find(Salle.class, salleId);

        // 3. Modifier et commiter avec em1 -> version passe à version + 1
        em1.getTransaction().begin();
        salleEm1.setNom("Salle Modifiée par EM1");
        em1.getTransaction().commit();
        em1.close();

        // 4. Tenter de modifier avec em2 -> version est obsolète -> OptimisticLockException
        em2.getTransaction().begin();
        salleEm2.setNom("Salle Modifiée par EM2 (Conflit attendu)");

        Assertions.assertThrows(Exception.class, () -> {
            em2.getTransaction().commit();
        }, "Un conflit de verrouillage optimiste (@Version) doit provoquer une exception");

        if (em2.getTransaction().isActive()) {
            em2.getTransaction().rollback();
        }
        em2.close();
    }

    @Test
    public void testJpqlAndCriteriaQueries() {
        em.getTransaction().begin();
        Utilisateur user = new Utilisateur("Recherche User", "recherche" + System.currentTimeMillis() + "@example.com");
        em.persist(user);

        Salle salle = new Salle("Salle JPQL Criteria", 40);
        em.persist(salle);

        LocalDateTime debut = LocalDateTime.now().plusDays(10);
        LocalDateTime fin = debut.plusHours(2);
        Reservation res = new Reservation(user, salle, debut, fin, "Réunion spécifique");
        em.persist(res);
        em.getTransaction().commit();

        ReservationService service = new ReservationService(em);

        // Test requête JPQL
        List<Reservation> jpqlList = service.findReservationsBySalleAndDate("Salle JPQL Criteria", LocalDateTime.now());
        Assertions.assertFalse(jpqlList.isEmpty(), "La requête JPQL doit retourner au moins une réservation");
        Assertions.assertEquals("Réunion spécifique", jpqlList.get(0).getMotif());

        // Test requête Criteria API
        List<Reservation> criteriaList = service.findReservationsCriteria("Salle JPQL Criteria", LocalDateTime.now());
        Assertions.assertFalse(criteriaList.isEmpty(), "La requête Criteria API doit retourner au moins une réservation");
        Assertions.assertEquals("Réunion spécifique", criteriaList.get(0).getMotif());
    }

    @Test
    public void testConflictDetection() {
        em.getTransaction().begin();
        Utilisateur user = new Utilisateur("User Conflit", "conflit" + System.currentTimeMillis() + "@example.com");
        em.persist(user);

        Salle salle = new Salle("Salle Conflit Test", 12);
        em.persist(salle);
        em.getTransaction().commit();

        ReservationService service = new ReservationService(em);

        LocalDateTime t1 = LocalDateTime.now().plusDays(20).withHour(10).withMinute(0);
        LocalDateTime t2 = t1.plusHours(2);

        // Première réservation valide
        Reservation r1 = new Reservation(user, salle, t1, t2, "Créneau 1");
        service.createReservation(r1);

        // Seconde réservation chevauchant le même créneau sur la même salle -> exception attendue
        LocalDateTime tChevauchementDebut = t1.plusHours(1);
        LocalDateTime tChevauchementFin = t2.plusHours(1);
        Reservation r2 = new Reservation(user, salle, tChevauchementDebut, tChevauchementFin, "Créneau en conflit");

        Assertions.assertThrows(IllegalStateException.class, () -> {
            service.createReservation(r2);
        }, "Une réservation sur un créneau déjà occupé doit lever une exception");
    }

    @Test
    public void testDeleteReservation() {
        em.getTransaction().begin();
        Utilisateur user = new Utilisateur("User Delete", "delete" + System.currentTimeMillis() + "@example.com");
        em.persist(user);

        Salle salle = new Salle("Salle Delete Test", 8);
        em.persist(salle);

        Reservation res = new Reservation(user, salle, LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(30).plusHours(1), "A supprimer");
        em.persist(res);
        em.getTransaction().commit();

        Long resId = res.getId();
        Assertions.assertNotNull(em.find(Reservation.class, resId));

        ReservationService service = new ReservationService(em);
        service.deleteReservation(resId);

        Assertions.assertNull(em.find(Reservation.class, resId), "La réservation doit avoir été supprimée");
    }
}
