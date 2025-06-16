package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "job")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jobPageUrl", nullable = false)
    private String jobPageUrl;

    @Column(name = "positionName", nullable = false)
    private String positionName;

    @Column(name = "organizationUrl", nullable = false)
    private String organizationUrl;

    @Column(name = "logoUrl")
    private String logoUrl;

    @Column(name = "organizationTitle", nullable = false)
    private String organizationTitle;

    @Column(name = "laborFunction", nullable = false)
    private String laborFunction;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "postedDate", nullable = false)
    private Long postedDate;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;
}