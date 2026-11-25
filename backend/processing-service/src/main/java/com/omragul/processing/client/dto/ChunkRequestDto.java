package com.omragul.processing.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkRequestDto {
    private String text;

    @JsonProperty("chunk_size")
    @Builder.Default
    private int chunkSize = 1000;

    @JsonProperty("chunk_overlap")
    @Builder.Default
    private int chunkOverlap = 200;

    public ChunkRequestDto(String text) {
        this.text = text;
        this.chunkSize = 1000;
        this.chunkOverlap = 200;
    }
}
