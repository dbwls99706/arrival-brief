package studio.ax.arrival
import org.junit.Assert.*
import org.junit.Test
class BriefEngineTest {
 @Test fun allEightOfficialMappings(){val cases=listOf("오디토리움" to "B3 블루존","그랜드볼룸" to "B3 블루존","아셈볼룸" to "B3 블루존","스튜디오159" to "B2 퍼플존","컨퍼런스룸 E" to "B2 퍼플존","THE PLATZ" to "B2 지하 2층","회의실 301호" to "B2 그린존","회의실 403호" to "B2 그린존");for((name,parking) in cases){val r=BriefEngine.resolve("코엑스 $name","1.65");assertEquals(name,"ready",r.status);assertEquals(parking,r.destination?.parking)}}
 @Test fun invalidAndExcessHeight(){assertEquals("blocked",BriefEngine.resolve("코엑스 오디토리움","2.21").status);for(v in listOf("","NaN","0","10"))assertEquals("invalid",BriefEngine.resolve("코엑스 오디토리움",v).status)}
 @Test fun ambiguityMustBeExplicit(){val t="코엑스 오디토리움과 컨퍼런스룸 E";assertEquals("select",BriefEngine.resolve(t,"1.65").status);assertEquals("conferenceE",BriefEngine.resolve(t,"1.65","conferenceE").destination?.id)}
 @Test fun conflictingVenueAndFloor(){for(t in listOf("코엑스가 아닌 킨텍스 오디토리움","코엑스 오디토리움 2층","코엑스 회의실301호는 취소, 목적지는 벡스코"))assertEquals("review",BriefEngine.resolve(t,"1.65").status)}
 @Test fun unknownAndOtherFacility(){assertEquals("unsupported",BriefEngine.resolve("코엑스 마곡 오디토리움","1.65").status);assertEquals("select",BriefEngine.resolve("코엑스 2026-10-28 13:00","1.65").status)}
 @Test fun outputHasSourceAndNoLoadingDock(){val s=BriefEngine.export(BriefEngine.resolve("코엑스 오디토리움","1.65"));assertTrue(s.contains(BriefEngine.SOURCE));assertFalse(s.contains("하역장"));assertTrue(s.contains("실시간"))}
 @Test fun cancelledAndFFloor(){for(t in listOf("코엑스 오디토리움 행사는 취소","코엑스 오디토리움이 아니다","코엑스 오디토리움 2F"))assertEquals("review",BriefEngine.resolve(t,"1.65").status)}
 @Test fun unconnectedVehicleFailsClosed(){assertEquals(VehicleMode.UNKNOWN,UnconnectedVehicleGateway().mode());assertFalse(UnconnectedVehicleGateway().isSimulation)}
}
