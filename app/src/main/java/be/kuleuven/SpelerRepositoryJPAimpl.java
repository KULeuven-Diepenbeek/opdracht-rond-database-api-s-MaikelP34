package be.kuleuven;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class SpelerRepositoryJPAimpl implements SpelerRepository {
  private final EntityManager em;
  public static final String PERSISTANCE_UNIT_NAME = "be.kuleuven.spelerhibernateTest";

  // Constructor
  public SpelerRepositoryJPAimpl(EntityManager entityManager) {
    this.em = entityManager;
  }

  @Override
  public void addSpelerToDb(Speler speler) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      em.persist(speler);
      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) tx.rollback();
      throw new InvalidSpelerException(" A PRIMARY KEY constraint failed" + e);
    }
  }

  @Override
  public Speler getSpelerByTennisvlaanderenId(int tennisvlaanderenId) {
    try {
      Speler speler = em.find(Speler.class, tennisvlaanderenId);
      if (speler == null) {
        throw new InvalidSpelerException("Invalid Speler met identification: " + tennisvlaanderenId);
      }
    } catch (Exception e) {
      throw new InvalidSpelerException("Invalid Speler met identification: " + tennisvlaanderenId);
    }
    return em.find(Speler.class, tennisvlaanderenId);
  }

  @Override
  public List<Speler> getAllSpelers() {
    TypedQuery<Speler> query = em.createQuery("SELECT s FROM Speler s", Speler.class);
    return query.getResultList();
  }

  @Override
  public void updateSpelerInDb(Speler speler) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      // Controleer of de speler al bestaat in de database
      Speler existingSpeler = em.find(Speler.class, speler.getTennisvlaanderenId()); // Verander 'getId()' naar de juiste identifier van 'Speler'
      if (existingSpeler == null) {
        throw new InvalidSpelerException("Invalid Speler met identification: "+speler.getTennisvlaanderenId());
      }
      em.merge(speler);
      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) tx.rollback();
      throw new InvalidSpelerException("Invalid Speler met identification: "+ e);
    }
  }
  
  @Override
  public void deleteSpelerInDb(int tennisvlaanderenId) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      Speler speler = em.find(Speler.class, tennisvlaanderenId);
      if (speler != null) {
        em.remove(speler);
      }
      else {
        throw new InvalidSpelerException("Invalid Speler met identification: "+ tennisvlaanderenId);
      }
      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) tx.rollback();
      throw new InvalidSpelerException("Invalid Speler met identification: "+ e);
    }
  }


  @Override
public String getHoogsteRankingVanSpeler(int tennisvlaanderenId) {
    Speler foundSpeler = em.find(Speler.class, tennisvlaanderenId);
    if (foundSpeler == null) {
        throw new InvalidSpelerException("Speler met tennisvlaanderenId " + tennisvlaanderenId);
    }

    String hoogsteRanking;

    try {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Wedstrijd> wedstrijdRoot = query.from(Wedstrijd.class);

        Predicate speler1Predicate = cb.equal(wedstrijdRoot.get("speler1Id"), tennisvlaanderenId);
        Predicate speler2Predicate = cb.equal(wedstrijdRoot.get("speler2Id"), tennisvlaanderenId);
        Predicate finalePredicate = cb.isNotNull(wedstrijdRoot.get("finale"));

        query.multiselect(
                wedstrijdRoot.get("tornooiId"),
                wedstrijdRoot.get("finale"),
                wedstrijdRoot.get("winnaarId")
        )
        .where(cb.and(cb.or(speler1Predicate, speler2Predicate), finalePredicate))
        .orderBy(cb.asc(wedstrijdRoot.get("finale")));  // Beste ranking = kleinste finale-waarde
          
        List<Object[]> results = em.createQuery(query)
                                   .setMaxResults(1)
                                   .getResultList();

        if (!results.isEmpty()) {
            Object[] result = results.get(0);
            int tornooiId = (Integer) result[0];
            int finale = (Integer) result[1];
            Integer winnaar = (Integer) result[2];

            // --- Hier apart de tornooi-naam ophalen ---
            Tornooi tornooi = em.find(Tornooi.class, tornooiId);
            String tornooiNaam = (tornooi != null) ? tornooi.getClubnaam() : "Onbekend Tornooi";

            // --- Finale mapping ---
            String finaleString;
            switch (finale) {
                case 1:
                    finaleString = (winnaar != null && winnaar == tennisvlaanderenId) ? "winst" : "finale";
                    break;
                case 2:
                    finaleString = "halve finale";
                    break;
                case 4:
                    finaleString = "kwart finale";
                    break;
                default:
                    finaleString = "plaats " + finale;
                    break;
            }

            hoogsteRanking = "Hoogst geplaatst in het tornooi van " + tornooiNaam + " met plaats in de " + finaleString;
        } else {
            hoogsteRanking = "Geen ranking gevonden voor speler met ID " + tennisvlaanderenId;
        }
    } catch (Exception e) {
        throw new RuntimeException("Error fetching highest ranking for tennisvlaanderenId " + tennisvlaanderenId, e);
    }

    return hoogsteRanking;
}

  
  

  
  
  

  @Override
  public void addSpelerToTornooi(int tornooiId, int tennisvlaanderenId) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();

      Speler speler = em.find(Speler.class, tennisvlaanderenId);
      Tornooi tornooi = em.find(Tornooi.class, tornooiId);

      speler.getTornooien().add(tornooi);
      em.merge(speler);

      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) tx.rollback();
      throw e;
    }
  }

  @Override
  public void removeSpelerFromTornooi(int tornooiId, int tennisvlaanderenId) {
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();

      Speler speler = em.find(Speler.class, tennisvlaanderenId);
      Tornooi tornooi = em.find(Tornooi.class, tornooiId);

      if (speler == null || tornooi == null) {
        throw new IllegalArgumentException("Speler or Tornooi not found");
      }

      speler.getTornooien().remove(tornooi);
      em.merge(speler);

      tx.commit();
    } catch (Exception e) {
      if (tx.isActive()) tx.rollback();
      throw e;
    }
  }
}