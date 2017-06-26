#ifndef __TS_EX_H__
#define __TS_EX_H__

#include "bitstream_ex.h"

#define PACKET_SIZE	188

//class CTSPacket : public CBitstream
//{
//public:
/*
	CTSPacket(int type)
		: CBitstream(type, NULL, PACKET_SIZE) {}

	virtual ~CTSPacket() {}
*/
    void TSPacket(int type) 
    {
        CBitstream(type, NULL, PACKET_SIZE);
    }

	void Add_TS_Header(
        int type,
		unsigned char	transport_error_indicator,
		unsigned char	payload_unit_start_indicator,
		unsigned char	transport_priority,
		unsigned short	PID,
		unsigned char	transport_scrambling_code,
		unsigned char	adaptation_field_control,
		unsigned char	continuity_counter)
	{
		Add(type, 0x47, 8); // sync_byte='0100 0111'(0x47)
		Add(type, transport_error_indicator, 1);
		Add(type, payload_unit_start_indicator, 1);
		Add(type, transport_priority, 1);
		Add(type, PID, 13);
		Add(type, transport_scrambling_code, 2);
		Add(type, adaptation_field_control, 2);
		Add(type, continuity_counter, 4);
	}
	void Parse_TS_Header(
        int type,
		unsigned char&	sync_byte,
		unsigned char&	transport_error_indicator,
		unsigned char&	payload_unit_start_indicator,
		unsigned char&	transport_priority,
		unsigned short&	PID,
		unsigned char&	transport_scrambling_code,
		unsigned char&	adaptation_field_control,
		unsigned char&	continuity_counter)
	{
		sync_byte			= (unsigned char)Get(type, 8); // sync_byte='0100 0111'(0x47)
		transport_error_indicator	= (unsigned char)Get(type, 1);
		payload_unit_start_indicator	= (unsigned char)Get(type,1);
		transport_priority		= (unsigned char)Get(type,1);
		PID				= (unsigned short)Get(type,13);
		transport_scrambling_code	= (unsigned char)Get(type,2);
		adaptation_field_control	= (unsigned char)Get(type,2);
		continuity_counter		= (unsigned char)Get(type,4);
	}
  
	void Add_adaptation_field(
        int type,
		unsigned short	adaptation_field_length,
		unsigned char	discontinuity_indicator,
		unsigned char	random_access_indicator,
		unsigned char	elementary_stream_priority_indicator,
		unsigned char	PCR_flag,
		unsigned char	OPCR_flag,
		unsigned char	splicing_point_flag,
		unsigned char	transport_private_data_flag,
		unsigned char	adaptation_field_extension_flag
		)
	{
		Add(type, adaptation_field_length, 8);
		if (0 < adaptation_field_length) {
			Add(type, discontinuity_indicator, 1);
			Add(type, random_access_indicator, 1);
			Add(type, elementary_stream_priority_indicator, 1);
			Add(type, PCR_flag, 1);
			Add(type, OPCR_flag, 1);
			Add(type, splicing_point_flag, 1);
			Add(type, transport_private_data_flag, 1);
			Add(type, adaptation_field_extension_flag, 1);
		}
	}
	void Parse_adaptation_field(
        int type,
		unsigned short&	adaptation_field_length,
		unsigned char&	discontinuity_indicator,
		unsigned char&	random_access_indicator,
		unsigned char&	elementary_stream_priority_indicator,
		unsigned char&	PCR_flag,
		unsigned char&	OPCR_flag,
		unsigned char&	splicing_point_flag,
		unsigned char&	transport_private_data_flag,
		unsigned char&	adaptation_field_extension_flag
		)
	{
		adaptation_field_length = (unsigned short)Get(type, 8);
		if (0 < adaptation_field_length) {
			discontinuity_indicator			= (unsigned char)Get(type, 1);
			random_access_indicator			= (unsigned char)Get(type, 1);
			elementary_stream_priority_indicator	= (unsigned char)Get(type, 1);
			PCR_flag				= (unsigned char)Get(type, 1);
			OPCR_flag				= (unsigned char)Get(type, 1);
			splicing_point_flag			= (unsigned char)Get(type, 1);
			transport_private_data_flag		= (unsigned char)Get(type, 1);
			adaptation_field_extension_flag		= (unsigned char)Get(type, 1);
		}
	}

	void Add_PAT(
        int type,
		unsigned short		section_length,
		unsigned short		transport_stream_id,
		unsigned char		version_number,
		unsigned char		current_next_indicator,
		unsigned char		section_number,
		unsigned char		last_section_number
		)
	{
		Add(type, 0, 8); // table_id=0
		Add(type,1, 1); // section_syntax_indicator=1
		Add(type,0, 1); // '0'
		Add(type,3, 2); // reserved
		Add(type,section_length, 12);
		Add(type,transport_stream_id, 16);
		Add(type,3, 2); // reserved
		Add(type,version_number, 5);
		Add(type,current_next_indicator, 1);
		Add(type,section_number, 8);
		Add(type,last_section_number, 8);
	}

	void Parse_PAT(
        int type,
		unsigned char&		table_id,
		unsigned char&		section_syntax_indicator,
		unsigned short&		section_length,
		unsigned short&		transport_stream_id,
		unsigned char&		version_number,
		unsigned char&		current_next_indicator,
		unsigned char&		section_number,
		unsigned char&		last_section_number
		)
	{
		table_id			= (unsigned char)Get(type,8); // table_id=0
		section_syntax_indicator	= (unsigned char)Get(type,1); // section_syntax_indicator=1
		Check(type,0, 1);						 // '0'
		Check(type,3, 2);						 // '11': reserved
		section_length			= (unsigned char)Get(type,12);
		transport_stream_id		= (unsigned char)Get(type,16);
		Check(type,3, 2);						 // '11': reserved
		version_number			= (unsigned char)Get(type,5);
		current_next_indicator		= (unsigned char)Get(type,1);
		section_number			= (unsigned char)Get(type,8);
		last_section_number		= (unsigned char)Get(type,8);
	}

	void Add_PAT_Program(
        int type,
		unsigned short		program_number,
		unsigned short		PID
		)
	{
		Add(type,program_number, 16);
		Add(type,7, 3); // reserved
		Add(type,PID, 13);
	}

	void Parse_PAT_Program(
        int type,
		unsigned short&		program_number,
		unsigned short&		PID
		)
	{
		program_number = (unsigned short)Get(type,16);
		Check(type,7, 3); // reserved
		PID = (unsigned short)Get(type,13);
	}

	void Add_PMT(
        int type,
		unsigned int		section_length,
		unsigned int		program_number,
		unsigned char		version_number,
		unsigned char		current_next_indicator,
		unsigned char		section_number,
		unsigned char		last_section_number,
		unsigned int		PCR_PID,
		unsigned int		program_info_length
		)
	{
		Add(type,2, 8); // table_id=2
		Add(type,1, 1); // section_syntax_indicator=1
		Add(type,0, 1); // '0'
		Add(type,3, 2); // reserved
		Add(type,section_length, 12);
		Add(type,program_number, 16);
		Add(type,3, 2); // reserved
		Add(type,version_number, 5);
		Add(type,current_next_indicator, 1);
		Add(type,section_number, 8);
		Add(type,last_section_number, 8);
		Add(type,7, 3); // reserved
		Add(type,PCR_PID, 13);
		Add(type,15, 4); // reserved
		Add(type,program_info_length, 12);
	}

	void Parse_PMT(
        int type,
		unsigned char&		table_id,
		unsigned char&		section_syntax_indicator,
		unsigned short&		section_length,
		unsigned short&		program_number,
		unsigned char&		version_number,
		unsigned char&		current_next_indicator,
		unsigned char&		section_number,
		unsigned char&		last_section_number,
		unsigned short&		PCR_PID,
		unsigned short&		program_info_length
		)
	{
        table_id					= (unsigned char)Check(type,2, 8); // table_id=2
        section_syntax_indicator	= (unsigned char)Check(type,1, 1); // section_syntax_indicator=1
        Check(type,0, 1);											  // '0'
        Check(type,3, 2);											  // reserved
        section_length				= (unsigned short)Get(type,12);
        program_number				= (unsigned short)Get(type,16);
        Check(type,3, 2);											  // reserved
        version_number				= (unsigned char)Get(type,5);
        current_next_indicator		= (unsigned char)Get(type,1);
        section_number				= (unsigned char)Get(type,8);
        last_section_number			= (unsigned char)Get(type,8);
        Check(type,7, 3);											  // reserved
        PCR_PID						= (unsigned short)Get(type,13);
        Check(type,15, 4);											  // reserved
        program_info_length			= (unsigned short)Get(type,12);
    }

	void Add_PMT_Program_elements(
        int type,
		unsigned char		stream_type,
		unsigned short		elementary_PID,
		unsigned short		ES_info_length
		)
	{
		Add(type,stream_type, 8);
		Add(type,0x7, 3); // reserved
		Add(type,elementary_PID, 13);
		Add(type,0xf, 4); // reserved
		Add(type,ES_info_length, 12);
	}

	void Parse_PMT_Program_elements(
        int type,
		unsigned char&		stream_type,
		unsigned short&		elementary_PID,
		unsigned short&		ES_info_length
		)
	{
        stream_type		= (unsigned char)Get(type,8);
        Check(type,0x7, 3);	  // reserved
        elementary_PID	= (unsigned short)Get(type,13);
        Check(type,0xf, 4);    // reserved
        ES_info_length	= (unsigned short)Get(type,12);
    }


	void Add_PCR(
	    int type,
		unsigned long long	program_clock_reference_base)
	{
		unsigned long long ullBase = (program_clock_reference_base/300);
		unsigned long ulExt = program_clock_reference_base%300;

		Add(type, ullBase, 33); // this will mask to 2^33
		Add(type, 0x3f, 6); // reserved
		Add(type, ulExt, 9);
	}



	void Parse_PCR(
        int type,
		unsigned long long&	program_clock_reference_base)
	{
		unsigned long long ullBase = 0;
		unsigned long ulExt = 0;

		ullBase			= (unsigned long long)Get(type, 33); // this will mask to 2^33
		Check(type, 0x3f, 6);   // reserved
		ulExt			= (unsigned long)Get(type, 9);
		
		program_clock_reference_base = ullBase*300 + ulExt;
	}


	void Add_OPCR(
        int type, 
		unsigned long long	original_program_clock_reference_base)
	{
		long long llBase = (original_program_clock_reference_base/300);
		unsigned long dwExt = original_program_clock_reference_base%300;

		Add(type, llBase, 33); // this will mask to 2^33
		Add(type, 0x3f, 6); // reserved
		Add(type, dwExt, 9);
	}

	void Parse_OPCR(
        int type, 
		unsigned long long&	original_program_clock_reference_base)
	{
		unsigned long long ullBase = 0;
		unsigned long ulExt = 0;

		ullBase			= (unsigned long long)Get(type, 33); // this will mask to 2^33
		Check(type, 0x3f, 6);   // reserved
		ulExt			= (unsigned long)Get(type, 9);
		
		original_program_clock_reference_base = ullBase*300 + ulExt;
	}

	void Add_splicing_point(
        int type, 
		unsigned char	splice_countdown)
	{
		Add(type, splice_countdown, 8);
	}

	void Parse_splicing_point(
        int type, 
		unsigned char&	splice_countdown)
	{
		splice_countdown = (unsigned char)Get(type, 8);
	}

	void Add_transport_private_data_length(
        int type, 
		unsigned char	transport_private_data_length)
	{
		Add(type, transport_private_data_length, 8);
	}

	void Parse_transport_private_data_length(
        int type, 
		unsigned char&	transport_private_data_length)
	{
		transport_private_data_length = (unsigned char)Get(type, 8);
	}

	void Add_registration_descriptor_top(
        int type, 
		unsigned char	descriptor_tag,
		unsigned char	descriptor_length,
		unsigned long	format_identifier)
	{
		Add(type, descriptor_tag, 8);
		Add(type, descriptor_length, 8);
		Add(type, format_identifier, 32);
	}

	void Parse_registration_descriptor_top(
        int type, 
		unsigned char&	descriptor_tag,
		unsigned char&	descriptor_length,
		unsigned long&	format_identifier)
	{
		descriptor_tag    = (unsigned char)Get(type, 8);
		descriptor_length = (unsigned char)Get(type, 8);
		format_identifier = (unsigned char)Get(type, 32);
	}

	void Add_adaptation_field_extension(
        int type, 
		unsigned char	adaptation_field_extension_length,
		unsigned char	ltw_flag,
		unsigned char	piecewise_rate_flag,
		unsigned char	seamless_splice_flag)
	{
		Add(type, adaptation_field_extension_length, 8);
		Add(type, ltw_flag, 1);
		Add(type, piecewise_rate_flag, 1);
		Add(type, seamless_splice_flag, 1);
		Add(type, 31, 5); // reserved
	}

	void Parse_adaptation_field_extension(
        int type, 
		unsigned char&	adaptation_field_extension_length,
		unsigned char&	ltw_flag,
		unsigned char&	piecewise_rate_flag,
		unsigned char&	seamless_splice_flag)
	{
        adaptation_field_extension_length = (unsigned char)Get(type, 8);
        ltw_flag						  = (unsigned char)Get(type, 1);
        piecewise_rate_flag				  = (unsigned char)Get(type, 1);
        seamless_splice_flag			  = (unsigned char)Get(type, 1);
        Check(type, 31, 5);						// reserved
    }

	void Add_adaptation_field_extension_ltw(
        int type, 
		unsigned char	ltw_valid,
		unsigned short	ltw_offset)
	{
		Add(type, ltw_valid, 1);
		Add(type, ltw_offset, 15);
	}

	void Parse_adaptation_field_extension_ltw(
        int type, 
		unsigned char&	ltw_valid,
		unsigned short&	ltw_offset)
	{
		ltw_valid  = (unsigned char)Get(type, 1);
		ltw_offset = (unsigned short)Get(type, 15);
	}

	void Add_adaptation_field_extension_piecewise_rate(
        int type, 
		unsigned long	piecewise_rate)
	{
		Add(type, 3, 2); // reserved
		Add(type, piecewise_rate, 22);
	}

	void Parse_adaptation_field_extension_piecewise_rate(
        int type,
		unsigned long&	piecewise_rate)
	{
		Check(type,3, 2);     // reserved
		piecewise_rate = (unsigned long)Get(type,22);
	}

	void Add_adaptation_field_extension_seamless_splice(
        int type,
		unsigned char	splice_type,
		unsigned long	DTS_next_AU)
	{
		Add(type,splice_type, 4);
		Add(type,DTS_next_AU >> 30, 3);
		Add(type,1, 1); // marker bit
		Add(type,DTS_next_AU >> 15, 15);
		Add(type,1, 1); // marker bit
		Add(type,DTS_next_AU, 15);
		Add(type,1, 1); // marker bit
	}

	void Parse_adaptation_field_extension_seamless_splice(
        int type,
		unsigned char&	splice_type,
		unsigned long&	DTS_next_AU)
	{
		splice_type  = (unsigned char)Get(type,4);
		DTS_next_AU  = (unsigned long)Get(type,3) << 30;
		Check(type,1, 1);   // marker bit
		DTS_next_AU |= (unsigned long)Get(type,15) << 15;
		Check(type,1, 1);   // marker bit
		DTS_next_AU |= (unsigned long)Get(type,15);
		Check(type,1, 1);   // marker bit
	}

	void Add_PES_packet_top(
        int type,
		unsigned char	stream_id,
		unsigned short	PES_packet_length)
	{
		Add(type,1, 24); // '0000 0000 0000 0000 0000 0001'
		Add(type,stream_id, 8);
		Add(type,PES_packet_length, 16);
	}

	void Parse_PES_packet_top(
        int type,
		unsigned char&	stream_id,
		unsigned short&	PES_packet_length)
	{
        Check(type,1, 24);       // '0000 0000 0000 0000 0000 0001'
        stream_id		  = (unsigned char)Get(type,8);
        PES_packet_length = (unsigned short)Get(type,16);
    }


	void Add_PES_header(
        int type, 
		unsigned char	PES_scrambling_control,
		unsigned char	PES_priority,
		unsigned char	data_alignment_indicator,
		unsigned char	copyright,
		unsigned char	original_or_copy,
		unsigned char	PTS_DTS_flag,
		unsigned char	ESCR_flag,
		unsigned char	ES_rate_flag,
		unsigned char	DSM_trick_mode_flag,
		unsigned char	additional_copy_info_flag,
		unsigned char	PES_CRC_flag,
		unsigned char	PES_extension_flag,
		unsigned char	PES_header_data_length
		)
	{
		Add(type, 2, 2); // '10'
		Add(type, PES_scrambling_control, 2);
		Add(type, PES_priority, 1);
		Add(type, data_alignment_indicator, 1);
		Add(type, copyright, 1);
		Add(type, original_or_copy, 1);
		Add(type, PTS_DTS_flag, 2);
		Add(type, ESCR_flag, 1);
		Add(type, ES_rate_flag, 1);
		Add(type, DSM_trick_mode_flag, 1);
		Add(type, additional_copy_info_flag, 1);
		Add(type, PES_CRC_flag, 1);
		Add(type, PES_extension_flag, 1);
		Add(type, PES_header_data_length, 8);
	}

	void Parse_PES_header(
        int type, 
		unsigned char&	PES_scrambling_control,
		unsigned char&	PES_priority,
		unsigned char&	data_alignment_indicator,
		unsigned char&	copyright,
		unsigned char&	original_or_copy,
		unsigned char&	PTS_DTS_flag,
		unsigned char&	ESCR_flag,
		unsigned char&	ES_rate_flag,
		unsigned char&	DSM_trick_mode_flag,
		unsigned char&	additional_copy_info_flag,
		unsigned char&	PES_CRC_flag,
		unsigned char&	PES_extension_flag,
		unsigned char&	PES_header_data_length
		)
	{
        Check(type, 2, 2); // '10'
        PES_scrambling_control	  = (unsigned char)Get(type, 2);
        PES_priority			  = (unsigned char)Get(type, 1);
        data_alignment_indicator  = (unsigned char)Get(type, 1);
        copyright				  = (unsigned char)Get(type, 1);
        original_or_copy		  = (unsigned char)Get(type, 1);
        PTS_DTS_flag			  = (unsigned char)Get(type, 2);
        ESCR_flag				  = (unsigned char)Get(type, 1);
        ES_rate_flag		      = (unsigned char)Get(type, 1);
        DSM_trick_mode_flag		  = (unsigned char)Get(type, 1);
        additional_copy_info_flag = (unsigned char)Get(type, 1);
        PES_CRC_flag			  = (unsigned char)Get(type, 1);
        PES_extension_flag        = (unsigned char)Get(type, 1);
        PES_header_data_length    = (unsigned char)Get(type, 8);
    }



	void Add_PES_header_PTS(
        int type, 
		unsigned long long PTS)
	{
		Add(type, 2, 4); // '0010'
		Add(type, PTS >> 30, 3);
		Add(type, 1, 1); // marker bit
		Add(type, PTS >> 15, 15);
		Add(type, 1, 1); // marker bit
		Add(type, PTS, 15);
		Add(type, 1, 1); // marker bit
	}

	void Parse_PES_header_PTS(
        int type, 
		unsigned long long& PTS)
	{
        Check(type, 2, 4);       // '0010'
        PTS				 = (unsigned long long)Get(type, 3) << 30;
        Check(type, 1, 1);       // marker bit
        PTS			    |= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);       // marker bit
        PTS				|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);       // marker bit
    }

	void Add_PES_header_PTS_DTS(
        int type, 
		unsigned long long PTS,
		unsigned long long DTS)
	{
		Add(type, 3, 4); // '0011'
		Add(type, PTS >> 30, 3);
		Add(type, 1, 1); // marker bit
		Add(type, PTS >> 15, 15);
		Add(type, 1, 1); // marker bit
		Add(type, PTS, 15);
		Add(type, 1, 1); // marker bit

		Add(type, 1, 4); // '0001'
		Add(type, DTS >> 30, 3);
		Add(type, 1, 1); // marker bit
		Add(type, DTS >> 15, 15);
		Add(type, 1, 1); // marker bit
		Add(type, DTS, 15);
		Add(type, 1, 1); // marker bit
	}

	void Parse_PES_header_PTS_DTS(
        int type, 
		unsigned long long& PTS,
		unsigned long long& DTS)
	{
		Check(type, 3, 4);       // '0011'
        PTS				 = (unsigned long long)Get(type, 3) << 30;
        Check(type, 1, 1);       // marker bit
        PTS			    |= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);       // marker bit
        PTS				|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);       // marker bit

		Check(type, 1, 4);       // '0001'
        DTS				 = (unsigned long long)Get(type, 3) << 30;
        Check(type, 1, 1);       // marker bit
        DTS			    |= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);       // marker bit
        DTS				|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);       // marker bit
	}

	void Add_PES_header_ESCR(
        int type, 
		unsigned long long ESCR)
	{
		unsigned long long ullBase = (ESCR/300);
		unsigned long ulExt = ESCR%300;

		Add(type, 0, 2); // reserved
		Add(type, ullBase >> 30, 3);
		Add(type, 1, 1); // marker bit
		Add(type, ullBase >> 15, 15);
		Add(type, 1, 1); // marker bit
		Add(type, ullBase, 15);
		Add(type, 1, 1); // marker bit
		Add(type, ulExt, 9);
		Add(type, 1, 1); // marker bit
	}

	void Parse_PES_header_ESCR(
        int type, 
		unsigned long long& ESCR)
	{
		unsigned long long ullBase = 0;
		unsigned long ulExt = 0;

		Check(type, 0, 2);       // reserved
        ullBase			 = (unsigned long long)Get(type, 3) << 30;
        Check(type, 1, 1);       // marker bit
        ullBase			|= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);       // marker bit
        ullBase			|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);       // marker bit
		ulExt            = (unsigned long)Get(type, 9);
		Check(type, 1, 1);       // marker bit

		ESCR = 300*ullBase + ulExt;
	}

	void Add_PES_header_ES_rate(
        int type, 
		unsigned long ES_rate)
	{
		Add(type, 1, 1); // marker bit
		Add(type, ES_rate, 22);
		Add(type, 1, 1); // marker bit
	}

	void Add_PES_header_DSM_trick_mode(
        int type, 
		unsigned char trick_mode_control,
		unsigned char field_id,
		unsigned char intrs_slice_refresh,
		unsigned char frequency_truncation,
		unsigned char rep_cntrl)
	{
		Add(type, trick_mode_control, 3);

		switch (trick_mode_control) {
		case 0: // '000' fast_forward
		case 3: // '011' fase_reverse
			Add(type, field_id, 2);
			Add(type, intrs_slice_refresh, 1);
			Add(type, frequency_truncation, 2);
			break;
		case 1: // '001' slow_motion
		case 4: // '100' slow_reverse
			Add(type, rep_cntrl, 5);
			break;
		case 2: // '010' freeze_frame
			Add(type, field_id, 2);
			Add(type, 7, 3); // reserved
			break;
		}
	}

	void Parsed_PES_header_DSM_trick_mode(
        int type, 
		unsigned char& trick_mode_control,
		unsigned char& field_id,
		unsigned char& intrs_slice_refresh,
		unsigned char& frequency_truncation,
		unsigned char& rep_cntrl)
	{
        trick_mode_control = (unsigned char)Get(type, 3);

        switch (trick_mode_control) {
        case 0: // '000' fast_forward
        case 3: // '011' fase_reverse
            field_id			 = (unsigned char)Get(type, 2);
            intrs_slice_refresh  = (unsigned char)Get(type, 1);
            frequency_truncation = (unsigned char)Get(type, 2);
            break;
        case 1:					   // '001' slow_motion
        case 4:					   // '100' slow_reverse
            rep_cntrl			 = (unsigned char)Get(type, 5);
            break;
        case 2:					   // '010' freeze_frame
            field_id			 = (unsigned char)Get(type, 2);
            Check(type, 7, 3);		   // reserved
            break;
        }
    }

	void Add_PES_header_additional_copy_info(
        int type, 
		unsigned char additional_copy_info)
	{
		Add(type, 1, 1); // marker_bit
		Add(type, additional_copy_info, 7);
	}

	void Parse_PES_header_additional_copy_info(
        int type, 
		unsigned char& additional_copy_info)
	{
        Check(type, 1, 1);		   // marker_bit
        additional_copy_info = (unsigned char)Get(type, 7);
    }

	void Add_PES_header_PES_CRC(
        int type, 
		unsigned short previous_PES_packet_CRC)
	{
		Add(type, previous_PES_packet_CRC, 16);
	}

	void Parse_PES_header_PES_CRC(
        int type, 
		unsigned short& previous_PES_packet_CRC)
	{
		previous_PES_packet_CRC = (unsigned short)Get(type, 16);
	}

	void Add_PES_header_PES_extension(
        int type, 
		unsigned char PES_private_data_flag,
		unsigned char pack_header_field_flag,
		unsigned char program_packet_sequence_counter_flag,
		unsigned char P_STD_buffer_flag,
		unsigned char PES_extension_flag_2)
	{
		Add(type, PES_private_data_flag, 1);
		Add(type, pack_header_field_flag, 1);
		Add(type, program_packet_sequence_counter_flag, 1);
		Add(type, P_STD_buffer_flag, 1);
		Add(type, 7, 3); // reserved
		Add(type, PES_extension_flag_2, 1);
	}

    /* test for wifi display */
    void Add_PES_header_PES_extension_no_reserved(
        int type, 
		unsigned char PES_private_data_flag,
		unsigned char pack_header_field_flag,
		unsigned char program_packet_sequence_counter_flag,
		unsigned char P_STD_buffer_flag,
		unsigned char PES_extension_flag_2)
	{
		Add(type, PES_private_data_flag, 1);
		Add(type, pack_header_field_flag, 1);
		Add(type, program_packet_sequence_counter_flag, 1);
		Add(type, P_STD_buffer_flag, 1);
		Add(type, 0, 3); // reserved
		Add(type, PES_extension_flag_2, 1);
	}

	void Parse_PES_header_PES_extension(
        int type, 
		unsigned char& PES_private_data_flag,
		unsigned char& pack_header_field_flag,
		unsigned char& program_packet_sequence_counter_flag,
		unsigned char& P_STD_buffer_flag,
		unsigned char& PES_extension_flag_2)
	{
        PES_private_data_flag				 = (unsigned char)Get(type, 1);
        pack_header_field_flag				 = (unsigned char)Get(type, 1);
        program_packet_sequence_counter_flag = (unsigned char)Get(type, 1);
        P_STD_buffer_flag					 = (unsigned char)Get(type, 1);
        Check(type, 7, 3);                           // reserved /* test for wifi display, to support some file don't following spec*/
        PES_extension_flag_2				 = (unsigned char)Get(type, 1);
    }

	void Add_PES_HDCP_private_data(
        int type, 
		unsigned long streamCtr,
		unsigned long long inputCtr
		)
	{
		Add(type, 0, 13); // reserved
		Add(type, streamCtr >> 30, 2);
		Add(type, 1, 1); // marker
		Add(type, streamCtr >> 15, 15);
		Add(type, 1, 1); // marker
		Add(type, streamCtr >> 0, 15);
		Add(type, 1, 1); // marker
		Add(type, 0, 11); // reserved

		Add(type, inputCtr >> 60, 4);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 45, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 30, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 15, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 0, 15);
		Add(type, 1, 1); // marker
            
        /* test for wifi display, stuffing bytes */
        Add(type, 0xffff, 16);
	}

    /*add by wuhan for win8.1*/
	void Add_PES_HDCP_private_data_one_stuff(
        int type, 
		unsigned long streamCtr,
		unsigned long long inputCtr
		)
	{
		Add(type, 0, 13); // reserved
		Add(type, streamCtr >> 30, 2);
		Add(type, 1, 1); // marker
		Add(type, streamCtr >> 15, 15);
		Add(type, 1, 1); // marker
		Add(type, streamCtr >> 0, 15);
		Add(type, 1, 1); // marker
		Add(type, 0, 11); // reserved

		Add(type, inputCtr >> 60, 4);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 45, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 30, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 15, 15);
		Add(type, 1, 1); // marker
		Add(type, inputCtr >> 0, 15);
		Add(type, 1, 1); // marker
            
        /* test for wifi display, stuffing bytes */
        Add(type, 0xff, 8);
	}

	/*end*/
    
    /* test for wifi display */
    void Add_PES_HDCP_private_data_no_stuffing(
            int type, 
            unsigned long streamCtr,
            unsigned long long inputCtr
            )
        {
            Add(type, 0, 13); // reserved
            Add(type, streamCtr >> 30, 2);
            Add(type, 0, 1); // marker
            Add(type, streamCtr >> 15, 15);
            Add(type, 0, 1); // marker
            Add(type, streamCtr >> 0, 15);
            Add(type, 0, 1); // marker
            Add(type, 0, 11); // reserved
    
            Add(type, inputCtr >> 60, 4);
            Add(type, 0, 1); // marker
            Add(type, inputCtr >> 45, 15);
            Add(type, 0, 1); // marker
            Add(type, inputCtr >> 30, 15);
            Add(type, 0, 1); // marker
            Add(type, inputCtr >> 15, 15);
            Add(type, 0, 1); // marker
            Add(type, inputCtr >> 0, 15);
            Add(type, 0, 1); // marker
    
        }

	void Parse_PES_HDCP_private_data(
        int type, 
		unsigned long& streamCtr,
		unsigned long long& inputCtr
		)
	{
        Check(type, 0, 13);          // reserved
        streamCtr			 = (unsigned long)Get(type, 2) << 30;
        Check(type, 1, 1);		   // marker bit
        streamCtr			|= (unsigned long)Get(type, 15) << 15;
        Check(type, 1, 1);		   // marker bit
        streamCtr			|= (unsigned long)Get(type, 15);
        Check(type, 1, 1);		   // marker bit
        Check(type, 0, 11);		   // reserved

        inputCtr			 = (unsigned long long)Get(type, 4) << 60;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 45;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 30;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);		   // marker bit

        /* test for wifi display, to get stuffing bytes */
        Get(type, 16);
    }

     /*add by wuhan for win8.1*/
    void Parse_PES_HDCP_private_data_one_stuff(
        int type, 
		unsigned long& streamCtr,
		unsigned long long& inputCtr
		)
    {
        Check(type, 0, 13);          // reserved
        streamCtr			 = (unsigned long)Get(type, 2) << 30;
        Check(type, 1, 1);		   // marker bit
        streamCtr			|= (unsigned long)Get(type, 15) << 15;
        Check(type, 1, 1);		   // marker bit
        streamCtr			|= (unsigned long)Get(type, 15);
        Check(type, 1, 1);		   // marker bit
        Check(type, 0, 11);		   // reserved

        inputCtr			 = (unsigned long long)Get(type, 4) << 60;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 45;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 30;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15) << 15;
        Check(type, 1, 1);		   // marker bit
        inputCtr			|= (unsigned long long)Get(type, 15);
        Check(type, 1, 1);		   // marker bit

        /* test for wifi display, to get stuffing bytes */
        Get(type, 8);

    }
	/*end*/
    
    /* test for wifi display */
    void Parse_PES_HDCP_private_data_no_stuffing(
            int type, 
            unsigned long& streamCtr,
            unsigned long long& inputCtr
            )
        {
            Check(type, 0, 13);          // reserved
            streamCtr            = (unsigned long)Get(type, 2) << 30;
            Check(type, 1, 1);         // marker bit
            streamCtr           |= (unsigned long)Get(type, 15) << 15;
            Check(type, 1, 1);         // marker bit
            streamCtr           |= (unsigned long)Get(type, 15);
            Check(type, 1, 1);         // marker bit
            Check(type, 0, 11);        // reserved
    
            inputCtr             = (unsigned long long)Get(type, 4) << 60;
            Check(type, 1, 1);         // marker bit
            inputCtr            |= (unsigned long long)Get(type, 15) << 45;
            Check(type, 1, 1);         // marker bit
            inputCtr            |= (unsigned long long)Get(type, 15) << 30;
            Check(type, 1, 1);         // marker bit
            inputCtr            |= (unsigned long long)Get(type, 15) << 15;
            Check(type, 1, 1);         // marker bit
            inputCtr            |= (unsigned long long)Get(type, 15);
            Check(type, 1, 1);         // marker bit
    
        }

	void Add_PES_LPCM_private_data(
        int type, 
		unsigned short frameSize, // two bytes
		unsigned char channels, // bits 7-4
		//unsigned short samplerate, // 48000, 4 => 96000, 5 => 192000
        unsigned int samplerate,
        unsigned char bitspersample // bits 8-7; 1 => 16 bps, 3 => 24 bps
		)							// bits 6-0 unused
	{
		Add(type, frameSize, 16);
		if (1 == channels)
			Add(type, 1, 4);
		else
			Add(type, 3, 4);
		if (192000 == samplerate)
			Add(type, 5, 4);
		else if (96000 == samplerate)
			Add(type, 4, 4);
		else // default to 48000
			Add(type, 1, 4);
		if (24 == bitspersample)
			Add(type, 3, 2);
		else // default to 16 bps
			Add(type, 1, 2);
		Add(type, 0, 6);
	}

	void Parse_PES_LPCM_private_data(
        int type, 
		unsigned short& frameSize, // two bytes
		unsigned char& channels, // bits 7-4
		unsigned long& samplerate, // 48000, 4 => 96000, 5 => 192000
		unsigned char& bitspersample // bits 8-7; 1 => 16 bps, 3 => 24 bps
		)							// bits 6-0 unused
	{
		frameSize = (unsigned short)Get(type, 16);
		channels = (unsigned char)Get(type, 4);
		samplerate = (unsigned long)Get(type, 4);
		if (5 == samplerate)
			samplerate = 192000;
		else if (4 == samplerate)
			samplerate = 96000;
		else // default to 48000
			samplerate = 48000;

		bitspersample = (unsigned char)Get(type, 2);
		if (3 == bitspersample)
			bitspersample = 24;
		else // default to 16 bps
			bitspersample = 16;
		Check(type, 0, 6);
	}

	void Add_PES_header_PES_extension_program_packet_sequence_counter(
        int type, 
		unsigned char program_packet_sequence_counter,
		unsigned char MPEG1_MPEG2_identifier,
		unsigned char original_stuff_length)
	{
		Add(type, 1, 1); // marker_bit
		Add(type, program_packet_sequence_counter, 7);
		Add(type, 1, 1); // marker_bit
		Add(type, MPEG1_MPEG2_identifier, 1);
		Add(type, original_stuff_length, 6);
	}

	void Parse_PES_header_PES_extension_program_packet_sequence_counter(
        int type, 
		unsigned char& program_packet_sequence_counter,
		unsigned char& MPEG1_MPEG2_identifier,
		unsigned char& original_stuff_length)
	{
        Check(type, 1, 1);					  // marker_bit
        program_packet_sequence_counter = (unsigned char)Get(type, 7);
        Check(type, 1, 1);					  // marker_bit
        MPEG1_MPEG2_identifier			= (unsigned char)Get(type, 1);
        original_stuff_length			= (unsigned char)Get(type, 6);
    }

	void Add_PES_header_PES_extension_P_STD_buffer(
        int type, 
		unsigned char P_STD_buffer_scale,
		unsigned short P_STD_buffer_size)
	{
		Add(type, 1, 2); // '01'
		Add(type, P_STD_buffer_scale, 1);
		Add(type, P_STD_buffer_size, 13);
	}

	void Parse_PES_header_PES_extension_P_STD_buffer(
        int type, 
		unsigned char& P_STD_buffer_scale,
		unsigned short& P_STD_buffer_size)
	{
        Check(type, 1, 2);		 // '01'
        P_STD_buffer_scale = (unsigned char)Get(type, 1);
        P_STD_buffer_size  = (unsigned short)Get(type, 13);
    }

	void Add_PES_header_PES_extension_flag_2(
        int type, 
		unsigned char PES_extension_field_length)
	{
		Add(type, 1, 1); // marker_bit
		Add(type, PES_extension_field_length, 7);
	}

	void Parse_PES_header_PES_extension_flag_2(
        int type, 
		unsigned char& PES_extension_field_length)
	{
		Check(type, 1, 1); // marker_bit
		PES_extension_field_length = (unsigned char)Get(type, 7);
	}

//};

#endif // __TS_EX_H__

