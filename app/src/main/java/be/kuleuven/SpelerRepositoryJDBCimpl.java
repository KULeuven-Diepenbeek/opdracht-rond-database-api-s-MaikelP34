package be.kuleuven;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SpelerRepositoryJDBCimpl implements SpelerRepository {
    private final Connection connection;

    public SpelerRepositoryJDBCimpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void addSpelerToDb(Speler speler) {
        // Controleer of de speler al bestaat
        try {
            // Probeer de speler op te halen; als deze bestaat, gooien we een exception.
            getSpelerByTennisvlaanderenId(speler.getTennisvlaanderenId());
            throw new RuntimeException(" A PRIMARY KEY constraint failed");
        } catch (InvalidSpelerException e) {
            // Verwacht: speler bestaat niet, dus ga verder met invoegen.
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO speler (tennisvlaanderenid, naam, punten) VALUES (?, ?, ?)")) {
            ps.setInt(1, speler.getTennisvlaanderenId());
            ps.setString(2, speler.getNaam());
            ps.setInt(3, speler.getPunten());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij toevoegen speler", e);
        }
    }

    @Override
    public Speler getSpelerByTennisvlaanderenId(int id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM speler WHERE tennisvlaanderenid = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Speler(
                        rs.getInt("tennisvlaanderenid"),
                        rs.getString("naam"),
                        rs.getInt("punten"));
            } else {
                throw new InvalidSpelerException(id +"");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij ophalen speler", e);
        }
    }

    @Override
    public List<Speler> getAllSpelers() {
        List<Speler> result = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM speler")) {
            while (rs.next()) {
                result.add(new Speler(
                        rs.getInt("tennisvlaanderenid"),
                        rs.getString("naam"),
                        rs.getInt("punten")));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij ophalen alle spelers", e);
        }
    }

    @Override
    public void updateSpelerInDb(Speler speler) {
        // Valideer dat de speler bestaat; als de speler niet bestaat, gooit getSpelerByTennisvlaanderenId een InvalidSpelerException.
        getSpelerByTennisvlaanderenId(speler.getTennisvlaanderenId());
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE speler SET naam = ?, punten = ? WHERE tennisvlaanderenid = ?")) {
            ps.setString(1, speler.getNaam());
            ps.setInt(2, speler.getPunten());
            ps.setInt(3, speler.getTennisvlaanderenId());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij updaten speler", e);
        }
    }

    @Override
    public void deleteSpelerInDb(int id) {
        // Valideer dat de speler bestaat; zo niet, gooit getSpelerByTennisvlaanderenId een InvalidSpelerException.
        getSpelerByTennisvlaanderenId(id);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM speler WHERE tennisvlaanderenid = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            // Als verwijderen mislukt, gooien we een InvalidSpelerException.
            throw new InvalidSpelerException("Fout bij verwijderen speler");
        }
    }

    @Override
    public String getHoogsteRankingVanSpeler(int tennisvlaanderenid) {
        // Valideer dat de speler bestaat.
        getSpelerByTennisvlaanderenId(tennisvlaanderenid);
        String resultaat;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT t.clubnaam, w.finale, w.winnaar " +
                "FROM wedstrijd w " +
                "JOIN tornooi t ON w.tornooi = t.id " +
                "WHERE (w.speler1 = ? OR w.speler2 = ?) AND w.finale IS NOT NULL " +
                "ORDER BY w.finale ASC LIMIT 1")) {
            ps.setInt(1, tennisvlaanderenid);
            ps.setInt(2, tennisvlaanderenid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String clubnaam = rs.getString("clubnaam");
                int finale = rs.getInt("finale");
                int winnaar = rs.getInt("winnaar");
                String plaats;
                if (finale == 1) {
                    plaats = (winnaar == tennisvlaanderenid) ? "winst" : "finale";
                } else if (finale == 2) {
                    plaats = "halve finale";
                } else if (finale == 4) {
                    plaats = "kwart finale";
                } else {
                    plaats = "plaats " + finale;
                }
                resultaat = "Hoogst geplaatst in het tornooi van " + clubnaam + " met plaats in de " + plaats;
            } else {
                resultaat = "Geen ranking gevonden voor speler met ID " + tennisvlaanderenid;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij ophalen hoogste ranking", e);
        }
        return resultaat;
    }

    // De originele methoden zonder spelerparameter gooien we nu een UnsupportedOperationException,
    // zodat gebruik in de tests via de overload met Speler gebeurt.
    @Override
    public void addSpelerToTornooi(int tornooiId, int tennisvlaanderenId) {
        // Valideer dat de speler bestaat.
        try {
            getSpelerByTennisvlaanderenId(tennisvlaanderenId);
        } catch (InvalidSpelerException e) {
            throw new RuntimeException("Speler bestaat niet", e);
        }
        // Valideer dat het tornooi bestaat.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM tornooi WHERE id = ?")) {
            ps.setInt(1, tornooiId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Tornooi bestaat niet");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij ophalen tornooi", e);
        }
        // Voeg de speler toe aan het tornooi.
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO speler_speelt_tornooi (tornooi, speler) VALUES (?, ?)")) {
            ps.setInt(1, tornooiId);
            ps.setInt(2, tennisvlaanderenId);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij toevoegen speler aan tornooi", e);
        }
    }

    @Override
    public void removeSpelerFromTornooi(int tornooiId, int tennisvlaanderenId) {

        // Valideer dat de speler bestaat.
        try {
            getSpelerByTennisvlaanderenId(tennisvlaanderenId);
        } catch (InvalidSpelerException e) {
            throw new RuntimeException("Speler bestaat niet", e);
        }
        // Valideer dat het tornooi bestaat.
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM tornooi WHERE id = ?")) {
            ps.setInt(1, tornooiId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Tornooi bestaat niet");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij ophalen tornooi", e);
        }
        // Verwijder de speler uit het tornooi.
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM speler_speelt_tornooi WHERE tornooi = ? AND speler = ?")) {
            ps.setInt(1, tornooiId);
            ps.setInt(2, tennisvlaanderenId);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Fout bij verwijderen speler uit tornooi", e);
        }

    }
}
