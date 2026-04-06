package com.pickbit.auctionservice.application.mapper;

import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.domain.Bid;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BidMapper {

    @Mapping(target = "auctionId", source = "auction.id")
    BidResponse toResponse(Bid bid);
}
