package com.faithlog.points;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface PointTransactionRepository extends JpaRepository<PointTransaction,Long>{
 Optional<PointTransaction> findByUserIdAndBusinessKey(Long userId,String key);
 List<PointTransaction> findByUserIdOrderByIdDesc(Long userId,Pageable page);
}
