package com.example.bulletinboard.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.bulletinboard.model.WithdrawalTask;
public interface WithdrawalTaskRepository extends JpaRepository<WithdrawalTask,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WithdrawalTask t where t.userId=:id")
    Optional<WithdrawalTask> lock(@Param("id") Long id);
    List<WithdrawalTask> findBySessionStatusNot(String status, Pageable page);
}
