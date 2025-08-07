package com.jobscraper.repository;

import com.jobscraper.entity.ListPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListPageRepository extends JpaRepository<ListPage, Integer> {
}