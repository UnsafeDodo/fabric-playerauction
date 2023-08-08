package unsafedodo.fabricauctionhouse.sql;

import unsafedodo.fabricauctionhouse.AuctionHouseMain;
import unsafedodo.fabricauctionhouse.auction.AuctionItem;
import unsafedodo.fabricauctionhouse.util.CommonMethods;

import java.sql.*;
import java.util.ArrayList;

public class SQLiteDatabaseManager implements DatabaseManager{
    public static String url = "jdbc:sqlite:auctionhousedb.db";

    private Connection connect(){
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(url);
        } catch (SQLException e){
            System.out.println(e.getMessage());
        }
        return connection;
    }

    public static void createTables(ArrayList<String> tableRegistry){
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()){
            for(String sql: tableRegistry){
                stmt.execute(sql);
            }
        } catch (SQLException e){
            System.out.println(e.getMessage());
        }
    }

    public static ArrayList<AuctionItem> getItemList(){
        String sql = "SELECT * FROM auctionhouse";
        ArrayList<AuctionItem> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
            while (rs.next()){
                list.add(new AuctionItem(rs.getInt("id"), rs.getString("playeruuid"), rs.getString("owner"), rs.getString("nbt"), rs.getString("item"), rs.getInt("count"), rs.getDouble("price"), rs.getLong("secondsLeft")));
            }
        }catch (SQLException e){
            e.printStackTrace();
        }

        return list;
    }

    public static ArrayList<AuctionItem> getExpiredItemsList(){
        String sql = "SELECT * FROM expireditems";
        ArrayList<AuctionItem> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new AuctionItem(rs.getInt("id"), rs.getString("playeruuid"), rs.getString("owner"), rs.getString("nbt"), rs.getString("item"), rs.getInt("count"), rs.getDouble("price"), 0));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    @Override
    public int addItemToAuction(String playeruuid, String owner, String nbt, String item, int count, double price, long secondsLeft) {
        String sql = "INSERT INTO auctionhouse(playeruuid,owner,nbt,item,count,price,secondsLeft) VALUES(?,?,?,?,?,?,?)";

        try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, playeruuid);
            pstmt.setString(2, owner);
            pstmt.setString(3, nbt);
            pstmt.setString(4, item);
            pstmt.setInt(5, count);
            pstmt.setDouble(6, price);
            pstmt.setLong(7, secondsLeft);
            pstmt.executeUpdate();
            CommonMethods.reloadHouse();
            return getMostRecentId();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public int getMostRecentId() {
        String sql = "SELECT id FROM auctionhouse ORDER BY id DESC";
        try (Connection conn = this.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
            return rs.getInt("id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public int playerItemCount(String playeruuid, String table) {
        String sql = "SELECT Count(*) FROM " + table + " WHERE playeruuid = '" + playeruuid + "'";
        try (Connection conn = this.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public boolean isItemForAuction(int id) {
        String sql = "SELECT Count(id) FROM auctionhouse WHERE id = " + id;
        try (Connection conn = this.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void updateTime(int id, long seconds) {
        String sql = "UPDATE auctionhouse SET secondsLeft = ? WHERE id = ?";

        try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, seconds);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeItemFromAuction(AuctionItem item) {
        String sql = "DELETE FROM auctionhouse WHERE id = ?";

        try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setInt(1, item.getId());
            pstmt.executeUpdate();
            AuctionHouseMain.ah.removeItem(item);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeItemFromExpired(AuctionItem item) {
        String sql = "DELETE FROM expireditems WHERE id = ?";

        try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            //sta parte qui a caso quando viene fatto return, e rifatto una seconda volta, contiene un item AIR che non dovrebbe esistere
            pstmt.setInt(1, item.getId());
            AuctionHouseMain.ei.removeItem(item);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void expireItem(AuctionItem item) {
        removeItemFromAuction(item);
        AuctionHouseMain.ah.removeItem(item);
        AuctionHouseMain.ei.addItem(item);
        String sql = "INSERT INTO expireditems(id,playeruuid,owner,nbt,item,count,price) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = this.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setInt(1, item.getId());
            pstmt.setString(2, item.getUuid());
            pstmt.setString(3, item.getOwner());
            pstmt.setString(4, item.getNbt());
            pstmt.setString(5, item.getName());
            pstmt.setInt(6, item.getItemStack().getCount());
            pstmt.setDouble(7, item.getPrice());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
