package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String jobPageUrl;
    private String positionName;
    private String organizationUrl;
    private String logoUrl;
    private String organizationTitle;
    private String laborFunction;
    private String location;
    private Long postedDate;
    private String description;
    private String tags;


}
