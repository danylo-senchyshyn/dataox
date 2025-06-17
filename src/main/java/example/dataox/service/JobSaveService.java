package example.dataox.service;

import example.dataox.entity.Job;
import example.dataox.repository.JobRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class JobSaveService {
    private final JobRepository jobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveJob(Job job) {
        jobRepository.save(job);
    }
}