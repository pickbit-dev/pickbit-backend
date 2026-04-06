package com.pickbit.auctionservice.application.mapper;

import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.api.dto.response.AuctionSummaryResponse;
import com.pickbit.auctionservice.domain.Auction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuctionMapper {

    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "updatedAt", source = "updatedDate")
    AuctionDetailResponse toDetailResponse(Auction auction);

    @Mapping(target = "createdAt", source = "createdDate")
    AuctionSummaryResponse toSummaryResponse(Auction auction);
}
