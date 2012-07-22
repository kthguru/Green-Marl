package backend_giraph;

import inc.GM_REDUCE_T;
import inc.GlobalMembersGm_backend_gps;
import inc.gm_compile_step;
import ast.AST_NODE_TYPE;
import ast.ast_foreach;
import ast.ast_if;
import ast.ast_procdef;
import ast.ast_sent;
import ast.ast_sentblock;
import ast.ast_typedecl;
import ast.ast_while;
import backend_cpp.*;
import backend_giraph.*;
import backend_gps.gm_gps_basic_block;
import backend_gps.gm_gps_bbtype_t;
import backend_gps.gm_gps_comm_t;
import backend_gps.gm_gps_comm_unit;
import backend_gps.gm_gps_gen;
import backend_gps.gm_gps_new_check_depth_two;
import backend_gps.gm_gps_new_check_pull_data;
import backend_gps.gm_gps_new_check_random_read;
import backend_gps.gm_gps_new_check_random_write;
import backend_gps.gm_gps_new_rewrite_rhs;
import backend_gps.gm_gps_opt_analyze_symbol_summary;
import backend_gps.gm_gps_opt_analyze_symbol_usage;
import backend_gps.gm_gps_opt_check_edge_value;
import backend_gps.gm_gps_opt_check_reverse_edges;
import backend_gps.gm_gps_opt_create_ebb;
import backend_gps.gm_gps_opt_find_congruent_message;
import backend_gps.gm_gps_opt_find_reachable;
import backend_gps.gm_gps_opt_merge_ebb_again;
import backend_gps.gm_gps_opt_merge_ebb_intra_loop;
import backend_gps.gm_gps_opt_split_comm_ebb;
import common.*;
import frontend.*;
import opt.*;
import tangible.*;

//-----------------------------------------------------------------
// interface for graph library Layer
//-----------------------------------------------------------------
// state number,
// begin sentence
// is pararell
public class gm_giraph_gen extends gm_gps_gen
{
	public gm_giraph_gen()
	{
		super();
		glib = new gm_giraphlib(this);
	}
	@Override
	public gm_giraphlib get_lib()
	{
		return glib;
	}

	public void init_gen_steps()
	{
		java.util.LinkedList<gm_compile_step> L = get_gen_steps();
		// no more change of AST at this point
		L.addLast(gm_gps_opt_check_reverse_edges.get_factory());
		L.addLast(gm_gps_new_check_depth_two.get_factory());
		L.addLast(gm_gps_new_check_pull_data.get_factory());
		L.addLast(gm_gps_new_check_random_read.get_factory());
		L.addLast(gm_gps_new_check_random_write.get_factory());
		L.addLast(gm_gps_opt_check_edge_value.get_factory());
		L.addLast(gm_gps_new_rewrite_rhs.get_factory());
    
		L.addLast(gm_gps_opt_create_ebb.get_factory());
		L.addLast(gm_gps_opt_split_comm_ebb.get_factory());
		L.addLast(gm_gps_opt_merge_ebb_again.get_factory());
		L.addLast(gm_gps_opt_merge_ebb_intra_loop.get_factory());
		L.addLast(gm_gps_opt_analyze_symbol_usage.get_factory());
		L.addLast(gm_gps_opt_analyze_symbol_summary.get_factory());
		L.addLast(gm_gps_opt_find_reachable.get_factory());
		L.addLast(gm_gps_opt_find_congruent_message.get_factory());
    
		L.addLast(gm_giraph_gen_class.get_factory());
	}

	//----------------------------------
	// stages in backend gen
	//----------------------------------

	public void write_headers()
	{
		get_lib().generate_headers(Body);
		Body.NL();
	}
	public void begin_class()
	{
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		Body.push("public class ");
		Body.push(proc.get_procname().get_genname());
		Body.push(" implements Tool {");
		Body.NL();
		Body.NL();
	}
	public void end_class()
	{
		Body.pushln("}");
	}

	public void do_generate_global_variables()
	{
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		String temp = new String(new char[1024]);
		Body.pushln("// Class logger");
		String.format(temp, "private static final Logger LOG = Logger.getLogger(%s.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		Body.NL();
		Body.pushln("// Configuration");
		Body.pushln("private Configuration conf;");
		Body.NL();
	}
	public void do_generate_master()
	{
		do_generate_shared_variables_keys();
		set_master_generate(true);
		do_generate_master_class();
		do_generate_master_scalar();
		do_generate_master_states();
		do_generate_master_serialization();
		Body.pushln("}"); // finish master class
		Body.NL();
    
	}
	public void do_generate_master_states()
	{
		String temp = new String(new char[1024]);
    
		Body.pushln("//----------------------------------------------------------");
		Body.pushln("// Master's State-machine ");
		Body.pushln("//----------------------------------------------------------");
		Body.pushln("private void _master_state_machine() {");
		Body.pushln("_master_should_start_workers = false;");
		Body.pushln("_master_should_finish = false;");
		Body.pushln("do {");
		Body.pushln("_master_state = _master_state_nxt ;");
		Body.pushln("switch(_master_state) {");
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
    
		java.util.LinkedList<gm_gps_basic_block> bb_blocks = info.get_basic_blocks();
		java.util.Iterator<gm_gps_basic_block> I;
    
		for (I = bb_blocks.iterator(); I.hasNext();)
		{
			gm_gps_basic_block b = I.next();
			int id = b.get_id();
			String.format(temp, "case %d: _master_state_%d(); break;", id, id);
			Body.pushln(temp);
		}
		Body.pushln("}");
		Body.pushln("} while (!_master_should_start_workers && !_master_should_finish);");
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("//@ Override");
		Body.pushln("public void compute() {");
		Body.pushln("// Graph size is not available in superstep 0");
		Body.pushln("if (getSuperstep() == 0) {");
		Body.pushln("return;");
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("_master_state_machine();");
		Body.NL();
    
		Body.pushln("if (_master_should_finish) { ");
		Body.pushln("haltComputation();");
		Body.pushln("writeOutput();");
		Body.pushln("}");
    
		Body.pushln("}");
		Body.NL();
    
		GlobalMembersGm_reproduce.gm_redirect_reproduce(f_body); // for temporary
		GlobalMembersGm_reproduce.gm_baseindent_reproduce(3);
    
		for (I = bb_blocks.iterator(); I.hasNext();)
		{
			gm_gps_basic_block b = I.next();
			do_generate_master_state_body(b);
		}
		GlobalMembersGm_reproduce.gm_redirect_reproduce(stdout);
		GlobalMembersGm_reproduce.gm_baseindent_reproduce(0);
	}
	public void do_generate_master_class()
	{
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
    
		//--------------------------------------------------------------------
		// create master class
		//--------------------------------------------------------------------
		String temp = new String(new char[1024]);
		String.format(temp, "public static class %sMaster extends MasterCompute {", proc.get_procname().get_genname());
		Body.pushln(temp);
		Body.pushln("// Control fields");
		boolean prep = GlobalMembersGm_main.FE.get_current_proc_info().find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_REVERSE_EDGE);
		String.format(temp, "private int     _master_state                = %d;", !prep ? 0 : GlobalMembersGm_backend_gps.GPS_PREPARE_STEP1);
		Body.pushln(temp);
		String.format(temp, "private int     _master_state_nxt            = %d;", !prep ? 0 : GlobalMembersGm_backend_gps.GPS_PREPARE_STEP1);
		Body.pushln(temp);
		Body.pushln("private boolean _master_should_start_workers = false;");
		Body.pushln("private boolean _master_should_finish        = false;");
		Body.NL();
    
		//--------------------------------------------------------------------
		// initialization function
		//--------------------------------------------------------------------
    
		Body.pushln("public void initialize() throws InstantiationException, IllegalAccessException {");
    
		// Basic blocks iterator
		java.util.LinkedList<gm_gps_basic_block> bb_blocks = info.get_basic_blocks();
		java.util.Iterator<gm_gps_basic_block> I_bb;
    
		// Symbol iterator
		java.util.HashSet<gm_symtab_entry> scalar = info.get_scalar_symbols();
		java.util.Iterator<gm_symtab_entry> I_sym;
    
		String.format(temp, "registerAggregator(%s, IntOverwriteAggregator.class);", GlobalMembersGm_backend_gps.GPS_KEY_FOR_STATE);
		Body.pushln(temp);
		for (I_bb = bb_blocks.iterator(); I_bb.hasNext();)
		{
			gm_gps_basic_block b = I_bb.next();
			if ((!b.is_prepare()) && (!b.is_vertex()))
				continue;
    
			if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
			{
				int cond_bb_no = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
				String.format(temp, "registerAggregator(\"%s%d\", BooleanOverwriteAggregator.class);", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, cond_bb_no);
				Body.pushln(temp);
			}
		}
    
		for (I_sym = scalar.iterator(); I_sym.hasNext();)
		{
			gm_symtab_entry sym = I_sym.next();
			gps_syminfo syminfo = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			assert syminfo != null;
    
			if ((syminfo.is_used_in_vertex() || syminfo.is_used_in_receiver()) && syminfo.is_used_in_master())
			{
				String.format(temp, "registerAggregator(%s, ", get_lib().create_key_string(sym.getId()));
				Body.push(temp);
				get_lib().generate_broadcast_variable_type(sym.getId().getTypeSummary(), Body, syminfo.get_reduce_type());
				Body.pushln(".class);");
			}
		}
    
		// Iterate symbol table
		gm_symtab args = proc.get_symtab_var();
		assert args != null;
		java.util.HashSet<gm_symtab_entry> syms = args.get_entries();
		java.util.Iterator<gm_symtab_entry> I;
		for (I = syms.iterator(); I.hasNext();)
		{
			gm_symtab_entry s = I.next();
    
			// check if used in master
			gps_syminfo syminfo = (gps_syminfo) s.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			if (!syminfo.is_used_in_master())
				continue;
    
			// input argument
			if (!s.getType().is_primitive() && (!s.getType().is_node()))
				continue;
    
			if (s.isReadable())
			{
	//C++ TO JAVA CONVERTER TODO TASK: Java does not have an equivalent for pointers to value types:
	//ORIGINAL LINE: sbyte* argname = s->getId()->get_genname();
				byte argname = s.getId().get_genname();
				String.format(temp, "%s = getContext().getConfiguration().", argname);
				Body.push(temp);
				switch (s.getType().getTypeSummary())
				{
					case GMTYPE_BOOL:
						String.format(temp, "getBoolean(\"%s\", false);", argname);
						break;
					case GMTYPE_INT:
						String.format(temp, "getInt(\"%s\", -1);", argname);
						break;
					case GMTYPE_LONG:
						String.format(temp, "getLong(\"%s\", -1L);", argname);
						break;
					case GMTYPE_FLOAT:
						String.format(temp, "getFloat(\"%s\", -1.0f);", argname);
						break;
						//TODO Waiting for https://issues.apache.org/jira/browse/HADOOP-8415 to be accepted
						//case GMTYPE_DOUBLE: sprintf(temp, "getDouble(\"%s\", -1.0);", argname); break;
					case GMTYPE_DOUBLE:
						String.format(temp, "getFloat(\"%s\", -1.0f);", argname);
						break;
					case GMTYPE_NODE:
						get_lib().is_node_type_int() ? sprintf(temp, "getInteger(\"%s\", -1);", argname) : sprintf(temp, "getLong(\"%s\", -1L);", argname);
						break;
					default:
						assert false;
						break;
				}
				Body.pushln(temp);
			}
		}
		Body.pushln("}");
		Body.NL();
    
		//--------------------------------------------------------------------
		// A method that saves final output values
		//--------------------------------------------------------------------
		Body.pushln("//save output");
		Body.pushln("public void writeOutput() {");
		ast_typedecl t = proc.get_return_type();
		if ((t != null) && (!t.is_void()))
		{
			String.format(temp, "System.out.println(\"%s:\\t\" + %s + \"\\n\");", GlobalMembersGm_backend_gps.GPS_RET_VALUE, GlobalMembersGm_backend_gps.GPS_RET_VALUE);
			Body.pushln(temp);
		}
		for (I = syms.iterator(); I.hasNext();)
		{
			gm_symtab_entry s = I.next();
    
			// output arguments
			if (!s.getType().is_primitive())
				continue;
			if (s.isWriteable())
			{
				String.format(temp, "System.out.println(\"%s:\\t\" + %s + \"\\n\");", s.getId().get_genname(), s.getId().get_genname());
				Body.pushln(temp);
			}
		}
		Body.pushln("}");
		Body.NL();
	}
	public void do_generate_master_scalar()
	{
		Body.pushln("//----------------------------------------------------------");
		Body.pushln("// Scalar Variables ");
		Body.pushln("//----------------------------------------------------------");
		String temp = new String(new char[1024]);
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		java.util.HashSet<gm_symtab_entry> scalar = info.get_scalar_symbols();
		java.util.Iterator<gm_symtab_entry> I;
    
		for (I = scalar.iterator(); I.hasNext();)
		{
			gm_symtab_entry e = I.next();
			gps_syminfo syminfo = (gps_syminfo) e.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			if (!syminfo.is_used_in_master())
				continue;
    
			String.format(temp, "private %s %s;", get_type_string(e.getType(), true), e.getId().get_genname());
			Body.pushln(temp);
		}
    
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		ast_typedecl t = proc.get_return_type();
		if ((t != null) && (!t.is_void()))
		{
			String.format(temp, "private %s %s; // the final return value of the procedure", get_type_string(t, true), GlobalMembersGm_backend_gps.GPS_RET_VALUE);
			Body.pushln(temp);
		}
    
		// Intra-Loop Merging
		if (proc.has_info(GlobalMembersGm_backend_gps.GPS_LIST_INTRA_MERGED_CONDITIONAL))
		{
			java.util.LinkedList<Object > L = proc.get_info_list(GlobalMembersGm_backend_gps.GPS_LIST_INTRA_MERGED_CONDITIONAL);
			java.util.Iterator<Object > l;
			for (l = L.iterator(); l.hasNext();)
			{
				gm_gps_basic_block bb = (gm_gps_basic_block)(l.next());
				String.format(temp, "private boolean %s%d = true;", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, bb.get_id());
				Body.pushln(temp);
			}
		}
    
		Body.NL();
	}
	public void do_generate_master_serialization()
	{
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		java.util.HashSet<gm_symtab_entry> scalar = info.get_scalar_symbols();
		get_lib().generate_master_class_details(scalar, Body);
	}
	public void do_generate_master_state_body(gm_gps_basic_block b)
	{
		int id = b.get_id();
		int type = b.get_type();
    
		String temp = new String(new char[1024]);
		String.format(temp, "private void _master_state_%d() {", id);
		Body.pushln(temp);
		Body.pushln("/*------");
		Body.flush();
		b.reproduce_sents();
		Body.pushln("-----*/");
		String.format(temp, "LOG.info(\"Running _master_state %d\");", id);
		Body.pushln(temp);
		if (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_BEGIN_VERTEX.getValue())
		{
    
			// generate Broadcast
			do_generate_scalar_broadcast_send(b);
			get_lib().generate_broadcast_state_master("_master_state", Body);
			if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
			{
				int cond_bb_no = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
				String.format(temp, "%s%d", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, cond_bb_no);
				get_lib().generate_broadcast_isFirst_master(temp, Body);
			}
			Body.NL();
    
			// generate next statement
			assert b.get_num_exits() == 1;
			int n = b.get_nth_exit(0).get_id();
			String.format(temp, "_master_state_nxt = %d;", n);
			Body.pushln(temp);
			Body.pushln("_master_should_start_workers = true;");
		}
		else if (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_SEQ.getValue())
		{
			if (b.is_after_vertex())
			{
				assert b.get_num_entries() == 1;
				do_generate_scalar_broadcast_receive(b);
			}
    
			// define local variables 
			java.util.HashMap<gm_symtab_entry, gps_syminfo> symbols = b.get_symbols();
			java.util.Iterator<gm_symtab_entry, gps_syminfo> I;
			for (I = symbols.iterator(); I.hasNext();)
			{
				gm_symtab_entry sym = I.next().getKey();
				gps_syminfo local_info = I.next().getValue();
				if (!local_info.is_scalar() || sym.isArgument()) //TODO: why is sym->isArgument() != local_info->is_argument() ?
					continue;
				gps_syminfo global_info = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
    
				if (!global_info.is_used_in_multiple_BB())
				{
					generate_scalar_var_def(sym, true);
				}
			}
			Body.NL();
    
			int cond_bb_no = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
    
			// generate sequential sentences
			b.prepare_iter();
			ast_sent s = b.get_next();
			while (s != null)
			{
				if (s.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
				{
					String.format(temp, "if (!%s%d) {", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, cond_bb_no);
					Body.pushln(temp);
				}
    
				generate_sent(s);
    
				if (s.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
				{
					Body.pushln("}");
				}
    
				s = b.get_next();
			}
    
			if (b.get_num_exits() == 0)
			{
				Body.pushln("_master_should_finish = true;");
			}
			else
			{
				int n = b.get_nth_exit(0).get_id();
				String.format(temp, "_master_state_nxt = %d;", n);
				Body.pushln(temp);
			}
		}
		else if (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_IF_COND.getValue())
		{
    
			Body.push("boolean _expression_result = ");
    
			// generate sentences
			ast_sent s = b.get_1st_sent();
			assert s != null;
			assert s.get_nodetype() == AST_NODE_TYPE.AST_IF;
			ast_if i = (ast_if) s;
			generate_expr(i.get_cond());
			Body.pushln(";");
    
			String.format(temp, "if (_expression_result) _master_state_nxt = %d;\nelse _master_state_nxt = %d;", b.get_nth_exit(0).get_id(), b.get_nth_exit(1).get_id());
			Body.pushln(temp);
		}
		else if (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_WHILE_COND.getValue())
		{
			ast_sent s = b.get_1st_sent();
			assert s != null;
			assert s.get_nodetype() == AST_NODE_TYPE.AST_WHILE;
			ast_while i = (ast_while) s;
			if (i.is_do_while())
				Body.pushln("// Do-While(...)");
			else
				Body.pushln("// While (...)");
    
			Body.NL();
			Body.push("boolean _expression_result = ");
			generate_expr(i.get_cond());
			Body.pushln(";");
    
			String.format(temp, "if (_expression_result) _master_state_nxt = %d;\nelse _master_state_nxt = %d;\n", b.get_nth_exit(0).get_id(), b.get_nth_exit(1).get_id()); // exit -  continue while
			Body.pushln(temp);
    
			if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
			{
				String.format(temp, "if (!_expression_result) %s%d=true; // reset is_first\n\n", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, b.get_id());
				Body.pushln(temp);
			}
    
		}
		else if ((type == gm_gps_bbtype_t.GM_GPS_BBTYPE_PREPARE1.getValue()) || (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_PREPARE2.getValue()))
		{
    
			// generate Broadcast
			do_generate_scalar_broadcast_send(b);
			get_lib().generate_broadcast_state_master("_master_state", Body);
    
			Body.pushln("// Preparation Step;");
			assert b.get_num_exits() == 1;
			int n = b.get_nth_exit(0).get_id();
			String.format(temp, "_master_state_nxt = %d;", n);
			Body.pushln(temp);
			Body.pushln("_master_should_start_workers = true;");
		}
		else if (type == gm_gps_bbtype_t.GM_GPS_BBTYPE_MERGED_TAIL.getValue())
		{
			Body.pushln("// Intra-Loop Merged");
			int source_id = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
			String.format(temp, "if (%s%d) _master_state_nxt = %d;", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, source_id, b.get_nth_exit(0).get_id());
			Body.pushln(temp);
			String.format(temp, "else _master_state_nxt = %d;", b.get_nth_exit(1).get_id());
			Body.pushln(temp);
			String.format(temp, "%s%d = false;\n", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, source_id);
			Body.pushln(temp);
		}
		else
		{
			assert false;
		}
    
		Body.pushln("}"); // end of state function
	}
	public void do_generate_scalar_broadcast_send(gm_gps_basic_block b)
	{
		// check if scalar variable is used inside the block
		java.util.HashMap<gm_symtab_entry, gps_syminfo> syms = b.get_symbols();
		java.util.Iterator<gm_symtab_entry, gps_syminfo> I;
		for (I = syms.iterator(); I.hasNext();)
		{
			gps_syminfo local_info = I.next().getValue();
			gps_syminfo global_info = (gps_syminfo) I.next().getKey().find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			if (!global_info.is_scalar())
				continue;
			if (local_info.is_used_as_reduce())
			{
				GM_REDUCE_T reduce_type = local_info.get_reduce_type();
    
				//printf("being used as reduce :%s\n", I->first->getId()->get_genname());
				get_lib().generate_broadcast_reduce_initialize_master(I.next().getKey().getId(), Body, reduce_type, GlobalMembersGm_giraph_gen_master.get_reduce_base_value(reduce_type, I.next().getKey().getType().getTypeSummary()));
				// [TODO] global argmax
				continue;
			}
			if (!global_info.is_used_in_master() && !global_info.is_argument())
				continue;
			if (local_info.is_used_as_rhs() && !global_info.is_argument())
			{
				// create a broad cast variable
				get_lib().generate_broadcast_send_master(I.next().getKey().getId(), Body);
			}
		}
	}
	public void do_generate_scalar_broadcast_receive(gm_gps_basic_block b)
	{
		assert b.get_num_entries() == 1;
		gm_gps_basic_block pred = b.get_nth_entry(0);
		assert pred.is_vertex();
    
		// check if scalar variable is modified inside the block
		java.util.HashMap<gm_symtab_entry, gps_syminfo> syms = pred.get_symbols();
		java.util.Iterator<gm_symtab_entry, gps_syminfo> I;
		for (I = syms.iterator(); I.hasNext();)
		{
			gps_syminfo local_info = I.next().getValue();
			gps_syminfo global_info = (gps_syminfo) I.next().getKey().find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			if (!global_info.is_scalar())
				continue;
			if (!global_info.is_used_in_master())
				continue;
			if (local_info.is_used_as_lhs() || local_info.is_used_as_reduce())
			{
				// create a broad cast variable
				get_lib().generate_broadcast_receive_master(I.next().getKey().getId(), Body, local_info.get_reduce_type());
			}
		}
	}
	public void do_generate_shared_variables_keys()
	{
		Body.pushln("// Keys for shared_variables ");
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		java.util.HashSet<gm_symtab_entry> scalar = info.get_scalar_symbols();
		java.util.Iterator<gm_symtab_entry> I;
    
		for (I = scalar.iterator(); I.hasNext();)
		{
			gm_symtab_entry sym = I.next();
			gps_syminfo syminfo = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			assert syminfo != null;
    
			// if the symbol is used in vertex and master
			// we need shared variable
			if ((syminfo.is_used_in_vertex() || syminfo.is_used_in_receiver()) && (syminfo.is_scoped_global()))
			{
				Body.push("private static final String ");
				Body.push(get_lib().create_key_string(sym.getId()));
				Body.push(" = ");
				Body.push("\"");
				Body.push(sym.getId().get_genname());
				Body.pushln("\";");
			}
		}
		Body.NL();
	}

	public void do_generate_vertex()
	{
		set_master_generate(false);
		do_generate_vertex_class();
		do_generate_worker_context_class();
		do_generate_vertex_property_class(false);
    
		if (GlobalMembersGm_main.FE.get_current_proc().find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_EDGE_PROP))
			do_generate_vertex_property_class(true);
    
		do_generate_message_class();
	}
	public void do_generate_worker_context_class()
	{
		String temp = new String(new char[1024]);
		String proc_name = GlobalMembersGm_main.FE.get_current_proc().get_procname().get_genname();
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
    
		// Basic blocks iterator
		java.util.LinkedList<gm_gps_basic_block> bb_blocks = info.get_basic_blocks();
		java.util.Iterator<gm_gps_basic_block> I_bb;
    
		// Symbol iterator
		java.util.HashSet<gm_symtab_entry> scalar = info.get_scalar_symbols();
		java.util.Iterator<gm_symtab_entry> I_sym;
    
		Body.pushln("//----------------------------------------------");
		Body.pushln("// Worker Context Class");
		Body.pushln("//----------------------------------------------");
		String.format(temp, "public static class %sWorkerContext extends WorkerContext {", proc_name);
		Body.pushln(temp);
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void preApplication() throws InstantiationException, IllegalAccessException {");
		String.format(temp, "registerAggregator(%s, IntOverwriteAggregator.class);", GlobalMembersGm_backend_gps.GPS_KEY_FOR_STATE);
		Body.pushln(temp);
    
		for (I_bb = bb_blocks.iterator(); I_bb.hasNext();)
		{
			gm_gps_basic_block b = I_bb.next();
			if ((!b.is_prepare()) && (!b.is_vertex()))
				continue;
    
			if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
			{
				int cond_bb_no = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
				String.format(temp, "registerAggregator(\"%s%d\", BooleanOverwriteAggregator.class);", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, cond_bb_no);
				Body.pushln(temp);
			}
		}
    
		for (I_sym = scalar.iterator(); I_sym.hasNext();)
		{
			gm_symtab_entry sym = I_sym.next();
			gps_syminfo syminfo = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			assert syminfo != null;
    
			if ((syminfo.is_used_in_vertex() || syminfo.is_used_in_receiver()) && syminfo.is_used_in_master())
			{
				String.format(temp, "registerAggregator(%s, ", get_lib().create_key_string(sym.getId()));
				Body.push(temp);
				get_lib().generate_broadcast_variable_type(sym.getId().getTypeSummary(), Body, syminfo.get_reduce_type());
				Body.pushln(".class);");
			}
		}
    
		Body.pushln("}");
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void postApplication() {");
		Body.pushln("}");
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void preSuperstep() {");
		String.format(temp, "useAggregator(%s);", GlobalMembersGm_backend_gps.GPS_KEY_FOR_STATE);
		Body.pushln(temp);
    
		for (I_bb = bb_blocks.iterator(); I_bb.hasNext();)
		{
			gm_gps_basic_block b = I_bb.next();
			if ((!b.is_prepare()) && (!b.is_vertex()))
				continue;
    
			if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
			{
				int cond_bb_no = b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO);
				String.format(temp, "useAggregator(\"%s%d\");", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, cond_bb_no);
				Body.pushln(temp);
			}
		}
    
		for (I_sym = scalar.iterator(); I_sym.hasNext();)
		{
			gm_symtab_entry sym = I_sym.next();
			gps_syminfo syminfo = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			assert syminfo != null;
    
			if ((syminfo.is_used_in_vertex() || syminfo.is_used_in_receiver()) && syminfo.is_used_in_master())
			{
				String.format(temp, "useAggregator(%s);", get_lib().create_key_string(sym.getId()));
				Body.pushln(temp);
			}
		}
    
		Body.pushln("}");
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void postSuperstep() {");
		Body.pushln("}");
		Body.NL();
		Body.pushln("} // end of worker context");
		Body.NL();
	}
	public void do_generate_vertex_property_class(boolean is_edge_prop)
	{
		Body.pushln("//----------------------------------------------");
		if (is_edge_prop)
			Body.pushln("// Edge Property Class");
		else
			Body.pushln("// Vertex Property Class");
		Body.pushln("//----------------------------------------------");
		String temp = new String(new char[1024]);
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		assert proc != null;
		String.format(temp, "public static class %s implements Writable {", is_edge_prop ? "EdgeData" : "VertexData");
		Body.pushln(temp);
    
		// list out property
		Body.pushln("// properties");
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		java.util.HashSet<gm_symtab_entry> prop = is_edge_prop ? info.get_edge_prop_symbols() : info.get_node_prop_symbols();
		java.util.Iterator<gm_symtab_entry> I;
		for (I = prop.iterator(); I.hasNext();)
		{
			gm_symtab_entry sym = I.next();
			//gps_syminfo* syminfo = (gps_syminfo*) sym->find_info(TAG_BB_USAGE);
			String.format(temp, "%s %s;", get_type_string(sym.getType().get_target_type(), is_master_generate()), sym.getId().get_genname());
    
			Body.pushln(temp);
		}
    
		if (GlobalMembersGm_main.FE.get_current_proc_info().find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_REVERSE_EDGE))
		{
			String.format(temp, "%s[] %s; //reverse edges (node IDs)", GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int() ? "IntWritable" : "LongWritable", GlobalMembersGm_backend_gps.GPS_REV_NODE_ID);
			Body.pushln(temp);
		}
    
		Body.NL();
		get_lib().generate_vertex_prop_class_details(prop, Body, is_edge_prop);
    
		Body.pushln("} // end of vertex property class");
		Body.NL();
    
	}
	public void do_generate_vertex_class()
	{
		String temp = new String(new char[1024]);
		String proc_name = GlobalMembersGm_main.FE.get_current_proc().get_procname().get_genname();
		Body.pushln("//----------------------------------------------");
		Body.pushln("// Main Vertex Class");
		Body.pushln("//----------------------------------------------");
		String.format(temp, "public static class %sVertex", proc_name);
		Body.pushln(temp);
		Body.push_indent();
		if (GlobalMembersGm_main.FE.get_current_proc().find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_EDGE_PROP))
		{
			String.format(temp, "extends EdgeListVertex< %s, VertexData, EdgeData, MessageData > {", GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int() ? "IntWritable" : "LongWritable");
		}
		else
		{
			String.format(temp, "extends EdgeListVertex< %s, VertexData, NullWritable, MessageData > {", GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int() ? "IntWritable" : "LongWritable");
		}
		Body.pushln(temp);
		Body.pop_indent();
    
		do_generate_vertex_states();
    
		Body.pushln("} // end of vertex class");
		Body.NL();
    
	}
	public void do_generate_message_class()
	{
		Body.pushln("//----------------------------------------------");
		Body.pushln("// Message Data ");
		Body.pushln("//----------------------------------------------");
    
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
		assert proc != null;
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		Body.pushln("public static class MessageData implements Writable {");
    
		Body.pushln("public MessageData() {}");
		Body.NL();
    
		if (info.is_single_message())
		{
			Body.pushln("//single message type; argument ignored");
			Body.pushln("public MessageData(byte type) {}");
		}
		else
		{
			Body.pushln("byte m_type;");
			Body.pushln("public MessageData(byte type) {m_type = type;}");
		}
		Body.NL();
    
		get_lib().generate_message_class_details(info, Body);
    
		Body.pushln("} // end of message-data class");
		Body.NL();
	}
	public void do_generate_vertex_states()
	{
		String temp = new String(new char[1024]);
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void compute(Iterator<MessageData> _msgs) {");
		get_lib().generate_receive_state_vertex("_state_vertex", Body);
    
		Body.pushln("switch(_state_vertex) { ");
		gm_gps_beinfo info = (gm_gps_beinfo) GlobalMembersGm_main.FE.get_current_backend_info();
		java.util.LinkedList<gm_gps_basic_block> bb_blocks = info.get_basic_blocks();
		java.util.Iterator<gm_gps_basic_block> I;
		int cnt = 0;
		for (I = bb_blocks.iterator(); I.hasNext();)
		{
			gm_gps_basic_block b = I.next();
			if ((!b.is_prepare()) && (!b.is_vertex()))
				continue;
			int id = b.get_id();
			String.format(temp, "case %d: _vertex_state_%d(_msgs); break;", id, id);
			Body.pushln(temp);
			cnt++;
		}
		if (cnt == 0)
		{
			Body.pushln("default: break;");
		}
		Body.pushln("}");
    
		Body.pushln("}");
    
		GlobalMembersGm_reproduce.gm_redirect_reproduce(f_body); // for temporary
		GlobalMembersGm_reproduce.gm_baseindent_reproduce(3);
		for (I = bb_blocks.iterator(); I.hasNext();)
		{
			gm_gps_basic_block b = I.next();
			if ((!b.is_prepare()) && (!b.is_vertex()))
				continue;
			do_generate_vertex_state_body(b);
		}
		GlobalMembersGm_reproduce.gm_redirect_reproduce(stdout);
		GlobalMembersGm_reproduce.gm_baseindent_reproduce(0);
	}
	public void do_generate_vertex_state_body(gm_gps_basic_block b)
	{
		int id = b.get_id();
		int type = b.get_type();
    
		String temp = new String(new char[1024]);
		String.format(temp, "private void _vertex_state_%d(Iterator<MessageData> _msgs) {", id);
		Body.pushln(temp);
    
		get_lib().generate_vertex_prop_access_prepare(Body);
    
		do_generate_vertex_state_receive_global(b);
    
		if (b.is_prepare())
		{
			get_lib().generate_prepare_bb(Body, b);
			Body.pushln("}");
			return;
		}
    
		assert type == gm_gps_bbtype_t.GM_GPS_BBTYPE_BEGIN_VERTEX.getValue();
		boolean is_conditional = b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL);
		String cond_var = new String(new char[128]);
		if (is_conditional)
			String.format(cond_var, "%s%d", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO));
    
		//---------------------------------------------------------
		// Generate Receiver Routine
		//---------------------------------------------------------
		if (b.has_receiver())
		{
			set_receiver_generate(true);
			Body.NL();
    
			if (is_conditional)
			{
				String.format(temp, "if (!%s) {", cond_var);
				Body.pushln(temp);
			}
    
			Body.pushln("// Begin msg receive");
			Body.pushln("while (_msgs.hasNext()) {");
			Body.pushln("MessageData _msg = _msgs.next();");
    
			java.util.LinkedList<gm_gps_comm_unit> R = b.get_receivers();
			java.util.Iterator<gm_gps_comm_unit> I;
			for (I = R.iterator(); I.hasNext();)
			{
				gm_gps_comm_unit U = I.next();
				if (U.get_type() == gm_gps_comm_t.GPS_COMM_NESTED)
				{
					ast_foreach fe = U.fe;
					assert fe != null;
    
					Body.pushln("/*------");
					Body.pushln("(Nested Loop)");
					Body.flush();
					if (is_conditional)
						GlobalMembersGm_reproduce.gm_baseindent_reproduce(5);
					else
						GlobalMembersGm_reproduce.gm_baseindent_reproduce(4);
					fe.reproduce(0);
					GlobalMembersGm_reproduce.gm_flush_reproduce();
					Body.pushln("-----*/");
					get_lib().generate_message_receive_begin(fe, Body, b, R.size() == 1);
    
					if (fe.get_body().get_nodetype() == AST_NODE_TYPE.AST_SENTBLOCK)
					{
						generate_sent_block((ast_sentblock) fe.get_body(), false);
					}
					else
					{
						generate_sent(fe.get_body());
					}
    
					get_lib().generate_message_receive_end(Body, R.size() == 1);
				}
				else
				{
					ast_sentblock sb = U.sb;
					assert sb != null;
					Body.pushln("/*------");
					Body.pushln("(Random Write)");
					Body.pushln("{");
					Body.flush();
					if (is_conditional)
						GlobalMembersGm_reproduce.gm_baseindent_reproduce(6);
					else
						GlobalMembersGm_reproduce.gm_baseindent_reproduce(5);
					java.util.LinkedList<ast_sent> sents = sb.get_sents();
					java.util.Iterator<ast_sent> I;
					for (I = sents.iterator(); I.hasNext();)
					{
						ast_sent s = I.next();
						if (s.find_info_ptr(GlobalMembersGm_backend_gps.GPS_FLAG_SENT_BLOCK_FOR_RANDOM_WRITE_ASSIGN) == sb)
							s.reproduce(0);
					}
					GlobalMembersGm_reproduce.gm_flush_reproduce();
					Body.pushln("}");
					Body.pushln("-----*/");
					get_lib().generate_message_receive_begin(sb, U.sym, Body, b, R.size() == 1);
    
					for (I = sents.iterator(); I.hasNext();)
					{
						ast_sent s = I.next();
						if (s.find_info_ptr(GlobalMembersGm_backend_gps.GPS_FLAG_SENT_BLOCK_FOR_RANDOM_WRITE_ASSIGN) == sb)
						{
							// implement receiving sentence
							generate_sent(s);
						}
					}
    
					get_lib().generate_message_receive_end(Body, R.size() == 1);
				}
			}
			set_receiver_generate(false);
			Body.pushln("}");
			if (is_conditional)
			{
				Body.pushln("}");
			}
			Body.NL();
			GlobalMembersGm_reproduce.gm_baseindent_reproduce(3);
		}
    
		//---------------------------------------------------------
		// Generate Main Routine
		//---------------------------------------------------------
		if (b.get_num_sents() > 0)
		{
			//assert (b->get_num_sents() == 1);
			Body.pushln("/*------");
			Body.flush();
			b.reproduce_sents(false);
			Body.pushln("-----*/");
			Body.NL();
    
			java.util.LinkedList<ast_sent> sents = b.get_sents();
			java.util.Iterator<ast_sent> I;
			int cnt = 0;
			for (I = sents.iterator(); I.hasNext();)
			{
				ast_sent s = I.next();
				assert s.get_nodetype() == AST_NODE_TYPE.AST_FOREACH;
				ast_foreach fe = (ast_foreach) s;
				ast_sent body = fe.get_body();
				if (cnt != 0)
					Body.NL();
				cnt++;
				if (fe.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
				{
					String.format(temp, "if (!%s)", cond_var);
					Body.push(temp);
					if (body.get_nodetype() != AST_NODE_TYPE.AST_SENTBLOCK)
						Body.pushln(" {");
					else
						Body.NL();
				}
    
				generate_sent(body);
    
				if (fe.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
				{
					if (body.get_nodetype() != AST_NODE_TYPE.AST_SENTBLOCK)
						Body.pushln("}");
				}
    
			}
		}
    
		Body.pushln("}");
	}
	public void do_generate_vertex_state_receive_global(gm_gps_basic_block b)
	{
    
		// load scalar variable
		java.util.HashMap<gm_symtab_entry, gps_syminfo> symbols = b.get_symbols();
		java.util.Iterator<gm_symtab_entry, gps_syminfo> I;
		for (I = symbols.iterator(); I.hasNext();)
		{
			gm_symtab_entry sym = I.next().getKey();
			gps_syminfo local_info = I.next().getValue();
			if (!local_info.is_scalar())
				continue;
    
			gps_syminfo global_info = (gps_syminfo) sym.find_info(GlobalMembersGps_syminfo.GPS_TAG_BB_USAGE);
			assert global_info != null;
    
			if (sym.getType().is_node_iterator())
			{
				// do nothing
			}
			else if (global_info.is_scoped_global())
			{
				if (local_info.is_used_as_rhs())
				{
					generate_scalar_var_def(sym, false);
					Body.push(" = ");
					if (global_info.is_argument())
					{
						// read the parameter
						get_lib().generate_parameter_read_vertex(sym.getId(), Body);
					}
					else
					{
						// receive it from Broadcast
						get_lib().generate_broadcast_receive_vertex(sym.getId(), Body);
					}
					Body.pushln(";");
				}
			}
			else
			{
				// temporary scalar variables. Define it here
				generate_scalar_var_def(sym, true);
			}
		}
    
		if (b.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_IS_INTRA_MERGED_CONDITIONAL))
		{
			String temp = new String(new char[1024]);
			String.format(temp, "%s%d", GlobalMembersGm_backend_gps.GPS_INTRA_MERGE_IS_FIRST, b.find_info_int(GlobalMembersGm_backend_gps.GPS_INT_INTRA_MERGED_CONDITIONAL_NO));
			get_lib().generate_receive_isFirst_vertex(temp, Body);
		}
	}

	public void do_generate_input_output_formats()
	{
		String temp = new String(new char[1024]);
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
    
		String proc_name = proc.get_procname().get_genname();
		String vertex_id = GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int() ? "IntWritable" : "LongWritable";
		String edge_data = proc.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_EDGE_PROP) ? "EdgeData" : "NullWritable";
    
		Body.pushln("//----------------------------------------------");
		Body.pushln("// Vertex Input format");
		Body.pushln("//----------------------------------------------");
    
		String.format(temp, "static class %sVertexInputFormat extends TextVertexInputFormat<%s, VertexData, %s, MessageData> {", proc_name, vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("@Override");
		String.format(temp, "public VertexReader<%s, VertexData, %s, MessageData>", vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("createVertexReader(InputSplit split, TaskAttemptContext context) throws IOException {");
		String.format(temp, "return new %sVertexReader(textInputFormat.createRecordReader(split, context));", proc_name);
		Body.pushln(temp);
		Body.pushln("}");
		Body.NL();
    
		String.format(temp, "static class %sVertexReader extends TextVertexInputFormat.TextVertexReader<%s, VertexData, %s, MessageData> {", proc_name, vertex_id, edge_data);
		Body.pushln(temp);
		String.format(temp, "public %sVertexReader(RecordReader<LongWritable, Text> lineRecordReader) {", proc_name);
		Body.pushln(temp);
		Body.pushln("super(lineRecordReader);");
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("@Override");
		String.format(temp, "public BasicVertex<%s, VertexData, %s, MessageData> getCurrentVertex() throws IOException, InterruptedException {", vertex_id, edge_data);
		Body.pushln(temp);
		String.format(temp, "BasicVertex<%s, VertexData, %s, MessageData> vertex =", vertex_id, edge_data);
		Body.pushln(temp);
		String.format(temp, "    BspUtils.<%s, VertexData, %s, MessageData> createVertex(getContext().getConfiguration());", vertex_id, edge_data);
		Body.pushln(temp);
		Body.NL();
    
		Body.pushln("Text line = getRecordReader().getCurrentValue();");
		Body.pushln("String[] values = line.toString().split(\"\\t\");");
		if (GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int())
		{
			Body.pushln("IntWritable vertexId = new IntWritable(Integer.parseInt(values[0]));");
		}
		else
		{
			Body.pushln("LongWritable vertexId = new LongWritable(Long.parseLong(values[0]));");
		}
		Body.pushln("double vertexValue = Double.parseDouble(values[1]);");
		String.format(temp, "Map<%s, %s> edges = Maps.newHashMap();", vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("for (int i = 2; i < values.length; i += 2) {");
		if (GlobalMembersGm_main.PREGEL_BE.get_lib().is_node_type_int())
		{
			Body.pushln("IntWritable edgeId = new IntWritable(Integer.parseInt(values[i]));");
		}
		else
		{
			Body.pushln("LongWritable edgeId = new LongWritable(Long.parseLong(values[i]));");
		}
		if (proc.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_EDGE_PROP))
		{
			Body.pushln("double edgeValue = Double.parseDouble(values[i+1]);");
			Body.pushln("edges.put(edgeId, new EdgeData(edgeValue));");
		}
		else
		{
			Body.pushln("edges.put(edgeId, NullWritable.get());");
		}
		Body.pushln("}");
		Body.pushln("vertex.initialize(vertexId, new VertexData(vertexValue), edges, null);");
		Body.pushln("return vertex;");
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("@Override");
		Body.pushln("public boolean nextVertex() throws IOException, InterruptedException {");
		Body.pushln("return getRecordReader().nextKeyValue();");
		Body.pushln("}");
		Body.pushln("}");
		Body.pushln("} // end of vertex input format");
		Body.NL();
    
		Body.pushln("// ----------------------------------------------");
		Body.pushln("// Vertex Output format");
		Body.pushln("// ----------------------------------------------");
		String.format(temp, "static class %sVertexOutputFormat extends", proc_name);
		Body.pushln(temp);
		String.format(temp, "TextVertexOutputFormat<%s, VertexData, %s> {", vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("@Override");
		String.format(temp, "public VertexWriter<%s, VertexData, %s> createVertexWriter(", vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("TaskAttemptContext context) throws IOException, InterruptedException {");
		String.format(temp, "return new %sVertexWriter(textOutputFormat.getRecordWriter(context));", proc_name);
		Body.pushln(temp);
		Body.pushln("}");
		Body.NL();
    
		String.format(temp, "static class %sVertexWriter", proc_name);
		Body.pushln(temp);
		String.format(temp, "extends TextVertexOutputFormat.TextVertexWriter<%s, VertexData, %s> {", vertex_id, edge_data);
		Body.pushln(temp);
		String.format(temp, "public %sVertexWriter(RecordWriter<Text, Text> lineRecordReader) {", proc_name);
		Body.pushln(temp);
		Body.pushln("super(lineRecordReader);");
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("@Override");
		Body.pushln("public void writeVertex(");
		String.format(temp, "BasicVertex<%s, VertexData, %s, ?> vertex)", vertex_id, edge_data);
		Body.pushln(temp);
		Body.pushln("throws IOException, InterruptedException {");
		Body.pushln("StringBuffer sb = new StringBuffer(vertex.getVertexId().toString());");
		Body.pushln("sb.append('\\t').append(vertex.getVertexValue());");
		Body.NL();
    
		String.format(temp, "Iterator<%s> outEdges = vertex.getOutEdgesIterator();", vertex_id);
		Body.pushln(temp);
		Body.pushln("while (outEdges.hasNext()) {");
		if (proc.find_info_bool(GlobalMembersGm_backend_gps.GPS_FLAG_USE_EDGE_PROP))
		{
			String.format(temp, "%s neighbor = outEdges.next();", vertex_id);
			Body.pushln(temp);
			Body.pushln("sb.append('\\t').append(neighbor);");
			Body.pushln("sb.append('\\t').append(vertex.getEdgeValue(neighbor));");
		}
		else
		{
			Body.pushln("sb.append('\\t').append(outEdges.next());");
			Body.pushln("sb.append(\"\\t1.0\");");
		}
		Body.pushln("}");
		Body.NL();
    
		Body.pushln("getRecordWriter().write(new Text(sb.toString()), null);");
		Body.pushln("}");
		Body.pushln("}");
		Body.pushln("} // end of vertex output format");
	}
	public void do_generate_job_configuration()
	{
		String temp = new String(new char[1024]);
		ast_procdef proc = GlobalMembersGm_main.FE.get_current_proc();
    
		// Iterate symbol table
		gm_symtab args = proc.get_symtab_var();
		assert args != null;
		java.util.HashSet<gm_symtab_entry> syms = args.get_entries();
		java.util.Iterator<gm_symtab_entry> I;
    
		Body.NL();
		Body.pushln("//----------------------------------------------");
		Body.pushln("// Job Configuration");
		Body.pushln("//----------------------------------------------");
		Body.pushln("@Override");
		Body.pushln("public final int run(final String[] args) throws Exception {");
		Body.pushln("Options options = new Options();");
		Body.pushln("options.addOption(\"h\", \"help\", false, \"Help\");");
		Body.pushln("options.addOption(\"v\", \"verbose\", false, \"Verbose\");");
		Body.pushln("options.addOption(\"w\", \"workers\", true, \"Number of workers\");");
		Body.pushln("options.addOption(\"i\", \"input\", true, \"Input filename\");");
		Body.pushln("options.addOption(\"o\", \"output\", true, \"Output filename\");");
		for (I = syms.iterator(); I.hasNext();)
		{
			gm_symtab_entry s = I.next();
			if (!s.getType().is_primitive() && (!s.getType().is_node()))
				continue;
			if (s.isReadable())
			{
				String.format(temp, "options.addOption(\"_%s\", \"%s\", true, \"%s\");", s.getId().get_genname(), s.getId().get_genname(), s.getId().get_genname());
				Body.pushln(temp);
			}
		}
		Body.pushln("HelpFormatter formatter = new HelpFormatter();");
		Body.pushln("if (args.length == 0) {");
		Body.pushln("formatter.printHelp(getClass().getName(), options, true);");
		Body.pushln("return 0;");
		Body.pushln("}");
		Body.pushln("CommandLineParser parser = new PosixParser();");
		Body.pushln("CommandLine cmd = parser.parse(options, args);");
		Body.pushln("if (cmd.hasOption('h')) {");
		Body.pushln("formatter.printHelp(getClass().getName(), options, true);");
		Body.pushln("return 0;");
		Body.pushln("}");
		Body.pushln("if (!cmd.hasOption('w')) {");
		Body.pushln("LOG.info(\"Need to choose the number of workers (-w)\");");
		Body.pushln("return -1;");
		Body.pushln("}");
		Body.pushln("if (!cmd.hasOption('i')) {");
		Body.pushln("LOG.info(\"Need to set input path (-i)\");");
		Body.pushln("return -1;");
		Body.pushln("}");
		for (I = syms.iterator(); I.hasNext();)
		{
			gm_symtab_entry s = I.next();
			if (!s.getType().is_primitive() && (!s.getType().is_node()))
				continue;
			if (s.isReadable())
			{
				String.format(temp, "if (!cmd.hasOption(\"%s\")) {", s.getId().get_genname());
				Body.pushln(temp);
				String.format(temp, "LOG.info(\"Need to set procedure argument (--%s)\");", s.getId().get_genname());
				Body.pushln(temp);
				Body.pushln("return -1;");
				Body.pushln("}");
			}
		}
		Body.NL();
		Body.pushln("GiraphJob job = new GiraphJob(getConf(), getClass().getName());");
		Body.pushln("job.getConfiguration().setInt(GiraphJob.CHECKPOINT_FREQUENCY, 0);");
		String.format(temp, "job.setMasterComputeClass(%sMaster.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		String.format(temp, "job.setVertexClass(%sVertex.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		String.format(temp, "job.setWorkerContextClass(%sWorkerContext.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		String.format(temp, "job.setVertexInputFormatClass(%sVertexInputFormat.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		Body.pushln("FileInputFormat.addInputPath(job.getInternalJob(), new Path(cmd.getOptionValue('i')));");
		Body.pushln("if (cmd.hasOption('o')) {");
		String.format(temp, "job.setVertexOutputFormatClass(%sVertexOutputFormat.class);", proc.get_procname().get_genname());
		Body.pushln(temp);
		Body.pushln("FileOutputFormat.setOutputPath(job.getInternalJob(), new Path(cmd.getOptionValue('o')));");
		Body.pushln("}");
		Body.pushln("int workers = Integer.parseInt(cmd.getOptionValue('w'));");
		Body.pushln("job.setWorkerConfiguration(workers, workers, 100.0f);");
		for (I = syms.iterator(); I.hasNext();)
		{
			gm_symtab_entry s = I.next();
			if (!s.getType().is_primitive() && (!s.getType().is_node()))
				continue;
			if (s.isReadable())
			{
	//C++ TO JAVA CONVERTER TODO TASK: Java does not have an equivalent for pointers to value types:
	//ORIGINAL LINE: sbyte* argname = s->getId()->get_genname();
				byte argname = s.getId().get_genname();
				String.format(temp, "job.getConfiguration().");
				Body.push(temp);
				switch (s.getType().getTypeSummary())
				{
					case GMTYPE_BOOL:
						String.format(temp, "setBoolean(\"%s\", Boolean.parseBoolean(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
					case GMTYPE_INT:
						String.format(temp, "setInt(\"%s\", Integer.parseInt(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
					case GMTYPE_LONG:
						String.format(temp, "setLong(\"%s\", Long.parseLong(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
					case GMTYPE_FLOAT:
						String.format(temp, "setFloat(\"%s\", Float.parseFloat(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
						//TODO Waiting for https://issues.apache.org/jira/browse/HADOOP-8415 to be accepted
						//case GMTYPE_DOUBLE: sprintf(temp, "setDouble(\"%s\", Double.parseDouble(cmd.getOptionValue(\"%s\")));", argname, argname); break;
					case GMTYPE_DOUBLE:
						String.format(temp, "setFloat(\"%s\", Float.parseFloat(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
					case GMTYPE_NODE:
						get_lib().is_node_type_int() ? sprintf(temp, "setInt(\"%s\", Integer.parseInt(cmd.getOptionValue(\"%s\")));", argname, argname) : sprintf(temp, "setLong(\"%s\", Long.parseLong(cmd.getOptionValue(\"%s\")));", argname, argname);
						break;
					default:
						assert false;
						break;
				}
				Body.pushln(temp);
			}
		}
		Body.NL();
		Body.pushln("boolean isVerbose = cmd.hasOption('v') ? true : false;");
		Body.pushln("if (job.run(isVerbose)) {");
		Body.pushln("return 0;");
		Body.pushln("} else {");
		Body.pushln("return -1;");
		Body.pushln("}");
		Body.pushln("} // end of job configuration");
    
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public Configuration getConf() {");
		Body.pushln("return conf;");
		Body.pushln("}");
    
		Body.NL();
		Body.pushln("@Override");
		Body.pushln("public void setConf(Configuration conf) {");
		Body.pushln("this.conf = conf;");
		Body.pushln("}");
    
		Body.NL();
		Body.pushln("public static void main(final String[] args) throws Exception {");
		String.format(temp, "System.exit(ToolRunner.run(new %s(), args));", proc.get_procname().get_genname());
		Body.pushln(temp);
		Body.pushln("}");
	}

	private gm_giraphlib glib; // graph library

	// from code generator interface
	public void generate_proc(ast_procdef proc)
	{
		write_headers();
		begin_class();
		do_generate_global_variables();
		do_generate_master();
    
		do_generate_vertex();
    
		do_generate_input_output_formats();
    
		do_generate_job_configuration();
    
		end_class();
	}

}