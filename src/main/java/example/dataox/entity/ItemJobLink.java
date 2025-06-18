package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@IdClass(ItemJobLinkId.class)
@Entity
@Table(name = "item_job_link")
public class ItemJobLink {

    @Id
    @Column(name = "jobPageUrl", nullable = false)
    private String jobPageUrl;

    @Id
    @Column(name = "jobPageUrlList", nullable = false)
    private String jobPageUrlList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jobPageUrl", referencedColumnName = "jobPageUrl", insertable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jobPageUrlList", referencedColumnName = "jobPageUrl", insertable = false, updatable = false)
    private ListPage listPage;
}