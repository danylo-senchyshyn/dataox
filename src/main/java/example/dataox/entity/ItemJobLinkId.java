package example.dataox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@EqualsAndHashCode
public class ItemJobLinkId implements Serializable {
    private String jobPageUrl;
    private String jobPageUrlList;
}