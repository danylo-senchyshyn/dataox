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

    @Column(name = "jobFunction")
    private String jobFunction;

    @Column(name = "numberOfFilteredJobs")
    private Integer numberOfFilteredJobs;

    @Column(name = "tags")
    private String tags;

    @Column(name = "jobPageUrl", unique = true, nullable = false)
    private String jobPageUrl;
}