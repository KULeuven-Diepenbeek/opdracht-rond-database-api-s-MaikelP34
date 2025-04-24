package be.kuleuven;

import java.util.List;

import org.checkerframework.checker.units.qual.t;
import org.jdbi.v3.core.Jdbi;

public class SpelerRepositoryJDBIimpl implements SpelerRepository {
    private final Jdbi jdbi;

    // Constructor: maak een Jdbi-instantie met de opgegeven connectiegegevens.
    public SpelerRepositoryJDBIimpl(String connectionString, String user, String pwd) {
        this.jdbi = Jdbi.create(connectionString, user, pwd);
    }

    @Override
    public void addSpelerToDb(Speler speler) {
        // Eerst proberen we de speler op te halen.
        // Als hij bestaat, gooien we een RuntimeException.
        try {
            Speler bestaand = getSpelerByTennisvlaanderenId(speler.getTennisvlaanderenId());
            // Als er geen exception werd gegooid, bestaat de speler al.
            throw new RuntimeException(" A PRIMARY KEY constraint failed");
        } catch (InvalidSpelerException e) {
            // Gewenste situatie: de speler bestaat nog niet, dus gaan we door met invoegen.
        }
        // Voer de INSERT uit.
        try {
            jdbi.useHandle(handle ->
                handle.createUpdate("INSERT INTO speler (tennisvlaanderenid, naam, punten) " +
                                    "VALUES (:id, :naam, :punten)")
                      .bind("id", speler.getTennisvlaanderenId())
                      .bind("naam", speler.getNaam())
                      .bind("punten", speler.getPunten())
                      .execute()
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij toevoegen speler", e);
        }
    }

    @Override
    public Speler getSpelerByTennisvlaanderenId(int tennisvlaanderenId) {
        // Zonder extra try-catch: als de speler niet gevonden wordt, gooit orElseThrow een InvalidSpelerException.
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT * FROM speler WHERE tennisvlaanderenid = :id")
                  .bind("id", tennisvlaanderenId)
                  .map((rs, ctx) -> new Speler(
                          rs.getInt("tennisvlaanderenid"),
                          rs.getString("naam"),
                          rs.getInt("punten")))
                  .findOne()
                  .orElseThrow(() -> new InvalidSpelerException("Invalid Speler met identification: " + tennisvlaanderenId))
        );
    }

    @Override
    public List<Speler> getAllSpelers() {
        try {
            return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM speler")
                      .map((rs, ctx) -> new Speler(
                          rs.getInt("tennisvlaanderenid"),
                          rs.getString("naam"),
                          rs.getInt("punten")))
                      .list()
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij ophalen alle spelers", e);
        }
    }

    @Override
    public void updateSpelerInDb(Speler speler) {
        // Valideer dat de speler bestaat. Als deze niet bestaat, gooit getSpelerByTennisvlaanderenId een InvalidSpelerException.
        getSpelerByTennisvlaanderenId(speler.getTennisvlaanderenId());
        try {
            jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE speler SET naam = :naam, punten = :punten " +
                                    "WHERE tennisvlaanderenid = :id")
                      .bind("naam", speler.getNaam())
                      .bind("punten", speler.getPunten())
                      .bind("id", speler.getTennisvlaanderenId())
                      .execute()
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij updaten speler", e);
        }
    }

    @Override
    public void deleteSpelerInDb(int tennisvlaanderenId) {
        // Valideer dat de speler bestaat. Als niet, gooit getSpelerByTennisvlaanderenId een InvalidSpelerException.
        getSpelerByTennisvlaanderenId(tennisvlaanderenId);
        try {
            jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM speler WHERE tennisvlaanderenid = :id")
                      .bind("id", tennisvlaanderenId)
                      .execute()
            );
        } catch (Exception e) {
            throw new InvalidSpelerException("Fout bij verwijderen speler");
        }
    }

    @Override
    public String getHoogsteRankingVanSpeler(int tennisvlaanderenId) {
        // Valideer dat de speler bestaat.
        getSpelerByTennisvlaanderenId(tennisvlaanderenId);
        try {
            return jdbi.withHandle(handle ->
                handle.createQuery("SELECT t.clubnaam, w.finale, w.winnaar " +
                                   "FROM wedstrijd w " +
                                   "JOIN tornooi t ON w.tornooi = t.id " +
                                   "WHERE (w.speler1 = :id OR w.speler2 = :id) AND w.finale IS NOT NULL " +
                                   "ORDER BY w.finale ASC LIMIT 1")
                      .bind("id", tennisvlaanderenId)
                      .map((rs, ctx) -> {
                          String clubnaam = rs.getString("clubnaam");
                          int finale = rs.getInt("finale");
                          int winnaar = rs.getInt("winnaar");
                          String plaats;
                          if (finale == 1) {
                              plaats = (winnaar == tennisvlaanderenId) ? "winst" : "finale";
                          } else if (finale == 2) {
                              plaats = "halve finale";
                          } else if (finale == 4) {
                              plaats = "kwart finale";
                          } else {
                              plaats = "plaats " + finale;
                          }
                          return "Hoogst geplaatst in het tornooi van " + clubnaam + " met plaats in de " + plaats;
                      })
                      .findOne()
                      .orElse("Geen ranking gevonden voor speler met ID " + tennisvlaanderenId)
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij ophalen hoogste ranking", e);
        }
    }


    @Override
    public void addSpelerToTornooi(int tornooiId, int tennisvlaanderenId) {
        try {
            jdbi.useHandle(handle ->
                handle.createUpdate("INSERT INTO speler_speelt_tornooi (tornooi, speler) VALUES (:tornooiId, :spelerId)")
                      .bind("tornooiId", tornooiId)
                      .bind("spelerId", tennisvlaanderenId)
                      .execute()
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij toevoegen speler aan tornooi", e);
        }
    }

    @Override
    public void removeSpelerFromTornooi(int tornooiId, int tennisvlaanderenId) {
        try {
            jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM speler_speelt_tornooi WHERE tornooi = :tornooi AND speler = :spelerId")
                      .bind("tornooi", tornooiId)
                      .bind("spelerId", tennisvlaanderenId)
                      .execute()
            );
        } catch (Exception e) {
            throw new RuntimeException("Fout bij verwijderen speler uit tornooi", e);
        }
    }
}
