//package com.lztek.api.demo;
//
//
//import androidx.room.Dao;
//import androidx.room.Delete;
//import androidx.room.Insert;
//import androidx.room.Query;
//import androidx.room.Update;
//
//import java.util.List;
//
//@Dao
//public interface SessionDao {
//    @Insert
//    long insert(Session session);
//
//    @Update
//    void update(Session session);
//
//    @Delete
//    void delete(Session session);
//
//    @Query("SELECT * FROM sessions")
//    List<Session> getAllSessions();
//
//    @Query("SELECT * FROM sessions WHERE id = :sessionId")
//    Session getSessionById(int sessionId);
//}


package com.lztek.api.demo;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SessionDao {
    @Insert
    long insert(Session session);

    @Update
    void update(Session session);

    @Delete
    void delete(Session session);

    @Query("SELECT * FROM sessions")
    List<Session> getAllSessions();

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    Session getSessionById(long sessionId);

    @Query("UPDATE sessions SET status = :status WHERE id = :sessionId")
    void updateStatus(int sessionId, String status);
}