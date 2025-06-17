package example.dataox.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class ItemJobLinkId implements Serializable {
    private String jobPageUrl;
    private String jobPageUrlList;
}