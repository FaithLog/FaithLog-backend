package com.faithlog.points;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface PointTransactionRepository extends JpaRepository<PointTransaction,Long>{
 Optional<PointTransaction> findByUserIdAndBusinessKey(Long userId,String key);
 @org.springframework.data.jpa.repository.Query("select entry.businessKey from PointTransaction entry where entry.userId=:userId and entry.businessKey in :keys")
 List<String> findBusinessKeysByUserIdAndBusinessKeyIn(@org.springframework.data.repository.query.Param("userId") Long userId,@org.springframework.data.repository.query.Param("keys") Collection<String> keys);
 List<PointTransaction> findByUserIdOrderByIdDesc(Long userId,Pageable page);
}
