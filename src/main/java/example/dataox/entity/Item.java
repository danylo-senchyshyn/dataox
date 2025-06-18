package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "item")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jobPageUrl", nullable = false)
    private String jobPageUrl;

    @Column(name = "positionName")
    private String positionName;

    @Column(name = "organizationUrl")
    private String organizationUrl;

    @Column(name = "logoUrl")
    private String logoUrl;

    @Column(name = "organizationTitle")
    private String organizationTitle;

    @Column(name = "laborFunction")
    private String laborFunction;

    @Column(name = "location")
    private String location;

    @Column(name = "postedDate")
    private Date postedDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}