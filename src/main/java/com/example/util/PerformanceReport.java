package com.example.util;

import org.hibernate.Session;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;

/**
 * Générateur de rapport de performance basé sur les statistiques Hibernate.
 */
public class PerformanceReport {

    private final EntityManager em;

    public PerformanceReport(EntityManager em) {
        this.em = em;
    }

    public void printReport() {
        Session session = em.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();

        long queryTotalTime = calculateQueryExecutionTotalTime(stats);

        System.out.println("=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Requêtes exécutées : " + stats.getQueryExecutionCount());
        System.out.println("Cache de second niveau hits : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache de second niveau misses : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache de second niveau mises à jour : " + stats.getSecondLevelCachePutCount());
        System.out.println("Transactions : " + stats.getTransactionCount());
        System.out.println("Durée totale des requêtes (ms) : " + queryTotalTime);
        System.out.println("Durée max d'une requête (ms) : " + stats.getQueryExecutionMaxTime());
        System.out.println("Entités insérées : " + stats.getEntityInsertCount());
        System.out.println("Entités chargées : " + stats.getEntityLoadCount());
        System.out.println("===============================");
    }

    private long calculateQueryExecutionTotalTime(Statistics stats) {
        long total = 0;
        for (String query : stats.getQueries()) {
            QueryStatistics qStats = stats.getQueryStatistics(query);
            if (qStats != null) {
                total += qStats.getExecutionTotalTime();
            }
        }
        return total;
    }

    public Statistics getStatistics() {
        Session session = em.unwrap(Session.class);
        return session.getSessionFactory().getStatistics();
    }
}
