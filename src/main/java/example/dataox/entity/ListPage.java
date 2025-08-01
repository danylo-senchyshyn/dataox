package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "listpage")
public class ListPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jobFunction", nullable = false)
    private String jobFunction;

    @Column(name = "numberOfFilteredJobs", nullable = false)
    private Integer numberOfFilteredJobs;

    @Column(name = "tags")
    private String tags;

    @Column(name = "jobPageUrl", nullable = false)
    private String jobPageUrl;
}