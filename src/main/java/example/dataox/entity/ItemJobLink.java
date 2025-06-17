package example.dataox.entity;

import jakarta.persistence.*;
import lombok.*;

@IdClass(ItemJobLinkId.class)
@Getter
@Setter
@Entity
@Table(name = "item_job_link")
public class ItemJobLink {

    @Id
    @Column(name = "jobPageUrl")
    private String jobPageUrl;

    @Id
    @Column(name = "jobPageUrlList")
    private String jobPageUrlList;

    @ManyToOne
    @JoinColumn(name = "jobPageUrl", referencedColumnName = "jobPageUrl", insertable = false, updatable = false)
    private Item item;

    @ManyToOne
    @JoinColumn(name = "jobPageUrlList", referencedColumnName = "jobPageUrl", insertable = false, updatable = false)
    private ListPage listPage;
}